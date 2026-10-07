package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.IcingAlertDetail;
import com.evops.dto.IcingAlertFilter;
import com.evops.dto.IcingAlertRow;
import com.evops.dto.IcingConfirmRequest;
import com.evops.dto.IcingEvaluateResult;
import com.evops.dto.IcingMonitorRequest;
import com.evops.dto.IcingScore;
import com.evops.entity.IcingAlert;
import com.evops.entity.IcingAlertConfirmation;
import com.evops.entity.IcingModelVersion;
import com.evops.entity.IcingMonitorData;
import com.evops.entity.Turbine;
import com.evops.enums.ConfirmationResult;
import com.evops.enums.DataQualityFlag;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingConfirmType;
import com.evops.enums.IcingRiskLevel;
import com.evops.mapper.IcingAlertConfirmationMapper;
import com.evops.mapper.IcingAlertMapper;
import com.evops.mapper.IcingMonitorDataMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 叶片结冰风险预警。
 *
 * 口径：
 * - 按温度、湿度、风速与历史振动四因子评分（IcingRiskEvaluator），达到模型阈值生成告警与停机建议并通知值班人员；
 * - 人工确认才停机，解除风险需再次人工确认（两次确认，解除可驳回后再确认）；
 * - 模型参数版本化：告警固化产生时的读数、得分、等级与模型版本，历史告警不随新参数重算；
 * - 读数缺失/异常标记 DATA_MISSING 待补，不评分不告警，补传后用当前模型重算；
 * - 预警列表统一经联表查询，风机置无效后页面、导出与后台同口径排除。
 */
@Service
public class IcingAlertService extends ServiceImpl<IcingAlertMapper, IcingAlert> {

    private static final AtomicLong NO_SEQ = new AtomicLong();

    private final IcingMonitorDataMapper monitorDataMapper;
    private final IcingAlertConfirmationMapper confirmationMapper;
    private final IcingModelService modelService;
    private final IcingRiskEvaluator evaluator;
    private final IcingNotificationService notificationService;
    private final TurbineService turbineService;
    private final AuditService auditService;

    public IcingAlertService(IcingMonitorDataMapper monitorDataMapper,
                             IcingAlertConfirmationMapper confirmationMapper,
                             IcingModelService modelService, IcingRiskEvaluator evaluator,
                             IcingNotificationService notificationService,
                             TurbineService turbineService, AuditService auditService) {
        this.monitorDataMapper = monitorDataMapper;
        this.confirmationMapper = confirmationMapper;
        this.modelService = modelService;
        this.evaluator = evaluator;
        this.notificationService = notificationService;
        this.turbineService = turbineService;
        this.auditService = auditService;
    }

    // ---------------- 监测数据上报与评估 ----------------

    /**
     * 上报一次监测数据并评估。
     * 同 风机+监测时刻 幂等：补传/补录/重试只更新该条并递增 revision。
     */
    @Transactional
    public IcingEvaluateResult report(IcingMonitorRequest req) {
        Turbine turbine = turbineService.mustGetActive(req.getTurbineId());
        LocalDateTime monitorTime = req.getMonitorTime() != null ? req.getMonitorTime() : LocalDateTime.now();

        IcingMonitorData data = monitorDataMapper.selectOne(new QueryWrapper<IcingMonitorData>()
                .eq("turbine_id", turbine.getId())
                .eq("monitor_time", monitorTime));
        boolean firstReport = data == null;
        if (firstReport) {
            data = new IcingMonitorData();
            data.setDataNo(StringUtils.hasText(req.getDataNo())
                    ? req.getDataNo() : "MD" + System.currentTimeMillis() + "-" + NO_SEQ.incrementAndGet());
            data.setTurbineId(turbine.getId());
            data.setMonitorTime(monitorTime);
            data.setRevision(0);
        }
        data.setTemperature(req.getTemperature());
        data.setHumidityPct(req.getHumidityPct());
        data.setWindSpeed(req.getWindSpeed());
        data.setVibration(req.getVibration());

        // 读数缺失或异常 → 标记待补，不评分、不告警
        List<String> missing = checkMissingOrAbnormal(req);
        IcingEvaluateResult result = new IcingEvaluateResult();
        result.setMonitorData(data);

        if (!missing.isEmpty()) {
            data.setQualityFlag(DataQualityFlag.DATA_MISSING);
            data.setMissingFields(String.join(",", missing));
            data.setScore(null);
            data.setRiskLevel(null);
            data.setModelVersionId(null);
            data.setEvaluatedAt(LocalDateTime.now());
            data.setRevision(data.getRevision() + 1);
            if (firstReport) {
                monitorDataMapper.insert(data);
            } else {
                monitorDataMapper.updateById(data);
            }
            result.setPendingSupplement(true);
            return result;
        }

        // 数据完整：按当前发布模型评分（新数据用新版本；历史告警仍固化旧版本不重算）
        IcingModelVersion model = modelService.mustGetCurrent();
        IcingScore score = evaluator.score(model, req.getTemperature(), req.getHumidityPct(),
                req.getWindSpeed(), req.getVibration());

        data.setQualityFlag(DataQualityFlag.NORMAL);
        data.setMissingFields(null);
        data.setScore(score.getTotal());
        data.setRiskLevel(score.getLevel());
        data.setModelVersionId(model.getId());
        data.setEvaluatedAt(LocalDateTime.now());
        data.setRevision(data.getRevision() + 1);
        if (firstReport) {
            monitorDataMapper.insert(data);
        } else {
            monitorDataMapper.updateById(data);
        }

        result.setModelVersion(model);
        result.setScore(score);

        // 达到阈值且当前无未闭环告警 → 生成告警与停机建议并通知值班人员（一轮结冰只告一次）
        if (score.getLevel() != IcingRiskLevel.NORMAL && !hasOpenAlert(turbine.getId())) {
            IcingAlert alert = createAlert(turbine, data, model, score);
            result.setAlert(alert);
        }
        return result;
    }

    /** 缺失字段（null）与异常字段（超出物理量程），非空即标记待补 */
    private List<String> checkMissingOrAbnormal(IcingMonitorRequest req) {
        List<String> problems = new ArrayList<>();
        if (req.getTemperature() == null) {
            problems.add("temperature(缺失)");
        } else if (req.getTemperature() < -60 || req.getTemperature() > 60) {
            problems.add("temperature(异常)");
        }
        if (req.getHumidityPct() == null) {
            problems.add("humidityPct(缺失)");
        } else if (req.getHumidityPct() < 0 || req.getHumidityPct() > 100) {
            problems.add("humidityPct(异常)");
        }
        if (req.getWindSpeed() == null) {
            problems.add("windSpeed(缺失)");
        } else if (req.getWindSpeed() < 0 || req.getWindSpeed() > 75) {
            problems.add("windSpeed(异常)");
        }
        if (req.getVibration() == null) {
            problems.add("vibration(缺失)");
        } else if (req.getVibration() < 0 || req.getVibration() > 50) {
            problems.add("vibration(异常)");
        }
        return problems;
    }

    private boolean hasOpenAlert(Long turbineId) {
        Long count = count(new QueryWrapper<IcingAlert>()
                .eq("turbine_id", turbineId)
                .in("status", IcingAlertStatus.ACTIVE, IcingAlertStatus.SHUTDOWN_CONFIRMED));
        return count > 0;
    }

    /** 生成告警（固化读数/得分/等级/模型版本）+ 停机建议，并与值班通知回执同事务落库 */
    private IcingAlert createAlert(Turbine turbine, IcingMonitorData data,
                                   IcingModelVersion model, IcingScore score) {
        IcingAlert alert = new IcingAlert();
        alert.setAlertNo("IA" + System.currentTimeMillis() + "-" + NO_SEQ.incrementAndGet());
        alert.setTurbineId(turbine.getId());
        alert.setMonitorDataId(data.getId());
        // 固化模型版本：历史告警不随模型参数调整重算
        alert.setModelVersionId(model.getId());
        alert.setModelVersionNo(model.getVersionNo());
        alert.setMonitorTime(data.getMonitorTime());
        alert.setTemperature(data.getTemperature());
        alert.setHumidityPct(data.getHumidityPct());
        alert.setWindSpeed(data.getWindSpeed());
        alert.setVibration(data.getVibration());
        alert.setScore(score.getTotal());
        alert.setRiskLevel(score.getLevel());
        alert.setShutdownAdvice(model.getShutdownAdvice());
        alert.setStatus(IcingAlertStatus.ACTIVE);
        save(alert);

        String content = "【叶片结冰" + (score.getLevel() == IcingRiskLevel.CRITICAL ? "危急" : "预警")
                + "】风机 " + turbine.getTurbineNo() + " 于 " + data.getMonitorTime()
                + " 风险得分 " + score.getTotal() + "（温度" + data.getTemperature() + "℃/湿度"
                + data.getHumidityPct() + "%/风速" + data.getWindSpeed() + "m·s⁻¹/振动"
                + data.getVibration() + "g，模型 v" + model.getVersionNo() + "）。停机建议："
                + model.getShutdownAdvice() + "。请人工确认后执行停机。";
        notificationService.notify(alert.getId(), turbine.getId(),
                IcingNotificationService.DUTY_OFFICER, content);

        auditService.record("ICING_ALERT", alert.getId(), "RAISE",
                "结冰风险 " + score.getLevel() + " 得分 " + score.getTotal()
                        + "，已发停机建议并通知值班人员（模型 v" + model.getVersionNo() + "）",
                IcingNotificationService.DUTY_OFFICER);
        return alert;
    }

    // ---------------- 两次人工确认 ----------------

    /**
     * 第一次人工确认：确认停机。系统只发建议，确认后才置为已停机；不确认不执行。
     */
    @Transactional
    public IcingAlert confirmShutdown(Long alertId, IcingConfirmRequest req) {
        IcingAlert alert = mustGetAlert(alertId);
        if (alert.getStatus() != IcingAlertStatus.ACTIVE) {
            throw new IllegalArgumentException("告警不在预警中状态，不能确认停机: " + alert.getStatus());
        }
        alert.setStatus(IcingAlertStatus.SHUTDOWN_CONFIRMED);
        alert.setShutdownConfirmedBy(req.getConfirmer());
        alert.setShutdownConfirmedAt(LocalDateTime.now());
        updateById(alert);

        recordConfirmation(alertId, IcingConfirmType.SHUTDOWN, ConfirmationResult.PASS, req);
        auditService.record("ICING_ALERT", alertId, "CONFIRM_SHUTDOWN",
                "人工确认执行停机：" + req.getConfirmer(), req.getConfirmer());
        return alert;
    }

    /**
     * 第二次人工确认：确认解除风险。
     * pass=true 闭环解除；pass=false 驳回，维持已停机、风险未解除，可再次确认。
     */
    @Transactional
    public IcingAlert confirmClear(Long alertId, boolean pass, IcingConfirmRequest req) {
        IcingAlert alert = mustGetAlert(alertId);
        if (alert.getStatus() != IcingAlertStatus.SHUTDOWN_CONFIRMED) {
            throw new IllegalArgumentException("告警未确认停机，不能确认解除: " + alert.getStatus());
        }
        if (!pass && !StringUtils.hasText(req.getOpinion())) {
            throw new IllegalArgumentException("驳回解除必须填写原因");
        }

        recordConfirmation(alertId, IcingConfirmType.CLEAR,
                pass ? ConfirmationResult.PASS : ConfirmationResult.FAIL, req);

        if (pass) {
            alert.setStatus(IcingAlertStatus.CLEARED);
            alert.setClearConfirmedBy(req.getConfirmer());
            alert.setClearConfirmedAt(LocalDateTime.now());
            alert.setClosedRemark(req.getOpinion());
            updateById(alert);
            auditService.record("ICING_ALERT", alertId, "CONFIRM_CLEAR",
                    "人工确认风险解除，告警闭环：" + req.getConfirmer(), req.getConfirmer());
        } else {
            // 驳回：状态不变，留痕，等待再次确认
            auditService.record("ICING_ALERT", alertId, "REJECT_CLEAR",
                    "解除确认被驳回，维持停机待查：" + req.getOpinion(), req.getConfirmer());
        }
        return alert;
    }

    private void recordConfirmation(Long alertId, IcingConfirmType type,
                                    ConfirmationResult decision, IcingConfirmRequest req) {
        IcingAlertConfirmation confirmation = new IcingAlertConfirmation();
        confirmation.setAlertId(alertId);
        confirmation.setConfirmType(type);
        confirmation.setDecision(decision);
        confirmation.setConfirmer(req.getConfirmer());
        confirmation.setOpinion(req.getOpinion());
        confirmation.setConfirmedAt(LocalDateTime.now());
        confirmationMapper.insert(confirmation);
    }

    // ---------------- 查询（页面 / 导出 / 后台 同口径） ----------------

    /**
     * 预警列表统一口径：联表风机并排除 run_status=INVALID 的无效运维对象。
     * 页面列表、CSV 导出与后台接口都调用本方法，保证同口径。
     */
    public List<IcingAlertRow> listAlerts(IcingAlertFilter filter, boolean onlyOpen) {
        Long turbineId = filter != null ? filter.getTurbineId() : null;
        IcingAlertStatus status = filter != null ? filter.getStatus() : null;
        IcingRiskLevel level = filter != null ? filter.getRiskLevel() : null;
        return baseMapper.selectRows(turbineId, status, level, onlyOpen);
    }

    /** 监测数据列表（可只看待补） */
    public List<IcingMonitorData> listMonitorData(Long turbineId, boolean onlyPendingSupplement) {
        return monitorDataMapper.selectList(new QueryWrapper<IcingMonitorData>()
                .eq(turbineId != null, "turbine_id", turbineId)
                .eq(onlyPendingSupplement, "quality_flag", DataQualityFlag.DATA_MISSING)
                .orderByDesc("monitor_time"));
    }

    /** 告警详情：告警本体 + 两次确认记录 + 通知回执（历史告警按其固化版本展示） */
    public IcingAlertDetail getAlertDetail(Long alertId) {
        IcingAlert alert = mustGetAlert(alertId);
        Turbine turbine = turbineService.getById(alert.getTurbineId());
        IcingAlertDetail detail = new IcingAlertDetail();
        detail.setAlert(alert);
        detail.setTurbineNo(turbine != null ? turbine.getTurbineNo() : null);
        detail.setSite(turbine != null ? turbine.getSite() : null);
        detail.setConfirmations(confirmationMapper.selectList(new QueryWrapper<IcingAlertConfirmation>()
                .eq("alert_id", alertId).orderByAsc("id")));
        detail.setNotifications(notificationService.listByAlert(alertId));
        return detail;
    }

    public IcingAlert mustGetAlert(Long id) {
        IcingAlert alert = getById(id);
        if (alert == null) {
            throw new IllegalArgumentException("结冰告警不存在: " + id);
        }
        return alert;
    }
}
