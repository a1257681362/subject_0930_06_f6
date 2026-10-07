package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.IcingObservationRequest;
import com.evops.dto.IcingResolveRequest;
import com.evops.dto.IcingShutdownConfirmRequest;
import com.evops.dto.IcingSupplementRequest;
import com.evops.dto.IcingWarningRow;
import com.evops.entity.Blade;
import com.evops.entity.IcingAlert;
import com.evops.entity.IcingModelParam;
import com.evops.entity.IcingNotification;
import com.evops.entity.IcingObservation;
import com.evops.entity.Turbine;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingNotifyType;
import com.evops.enums.IcingObservationStatus;
import com.evops.enums.IcingRiskLevel;
import com.evops.mapper.IcingAlertMapper;
import com.evops.mapper.IcingObservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 叶片结冰风险预警核心服务。
 *
 * 口径：
 * - 系统按温度、湿度、风速与历史振动计算风险分（{@link IcingRiskCalculator}），
 *   达到预警阈值生成预警并通知值班人员，达到停机阈值生成停机建议；
 * - 达到阈值不自动停机：人工确认停机建议后才执行停机，解除风险须再次确认；
 * - 告警时快照风险分、分项与所用参数版本——历史告警不随新参数重算；
 * - 温度/湿度/风速异常或缺失、历史振动缺失时标记 PENDING_SUPPLEMENT（待补），
 *   补传前不参与风险计算；补传后按首次上报时的同一参数版本重算；
 * - 无效叶片不参与预警：页面、导出与后台共用 {@link #listWarningRows}，同口径只统计有效叶片。
 */
@Service
public class IcingAlertService extends ServiceImpl<IcingAlertMapper, IcingAlert> {

    private static final AtomicLong SEQ = new AtomicLong();

    /** 值班通知接收人（联调期固定值班调度岗位） */
    public static final String DUTY_RECIPIENT = "值班调度";

    // 物理合理区间：越界视为异常数据，标记待补
    private static final double TEMP_MIN = -60.0;
    private static final double TEMP_MAX = 30.0;
    private static final double HUMIDITY_MIN = 0.0;
    private static final double HUMIDITY_MAX = 100.0;
    private static final double WIND_MIN = 0.0;
    private static final double WIND_MAX = 75.0;
    private static final double VIBRATION_MIN = 0.0;

    private final IcingObservationMapper observationMapper;
    private final IcingModelParamService paramService;
    private final IcingRiskCalculator calculator;
    private final IcingNotificationService notificationService;
    private final BladeService bladeService;
    private final TurbineService turbineService;
    private final AuditService auditService;

    public IcingAlertService(IcingObservationMapper observationMapper,
                             IcingModelParamService paramService,
                             IcingRiskCalculator calculator,
                             IcingNotificationService notificationService,
                             BladeService bladeService,
                             TurbineService turbineService,
                             AuditService auditService) {
        this.observationMapper = observationMapper;
        this.paramService = paramService;
        this.calculator = calculator;
        this.notificationService = notificationService;
        this.bladeService = bladeService;
        this.turbineService = turbineService;
        this.auditService = auditService;
    }

    // ---------------- 观测上报 ----------------

    /**
     * 上报一次观测：四项齐全且在合理区间才参与风险计算；
     * 任一异常/缺失标记 PENDING_SUPPLEMENT（待补），不生成告警。
     */
    @Transactional
    public IcingObservation report(IcingObservationRequest req) {
        Blade blade = bladeService.mustGet(req.getBladeId());
        assertValidBlade(blade);
        turbineService.mustGet(blade.getTurbineId());
        LocalDateTime observedAt = req.getObservedAt() != null ? req.getObservedAt() : LocalDateTime.now();

        String observationNo = uniqueNo(req.getObservationNo(), "IO");
        IcingObservation obs = new IcingObservation();
        obs.setObservationNo(observationNo);
        obs.setTurbineId(blade.getTurbineId());
        obs.setBladeId(blade.getId());
        obs.setObservedAt(observedAt);
        obs.setTemperatureC(req.getTemperatureC());
        obs.setHumidityPct(req.getHumidityPct());
        obs.setWindSpeedMs(req.getWindSpeedMs());
        obs.setVibration(req.getVibration());
        obs.setRevision(1);
        // 固化首次上报时的参数版本：补传重算沿用同一版本，不随参数调整改变
        obs.setParamVersionId(paramService.mustGetCurrent().getId());

        List<String> missing = findMissing(req.getTemperatureC(), req.getHumidityPct(),
                req.getWindSpeedMs(), req.getVibration());
        if (missing.isEmpty()) {
            obs.setStatus(IcingObservationStatus.OK);
            observationMapper.insert(obs);
            evaluateAndAlert(obs, req.getOperator());
        } else {
            obs.setStatus(IcingObservationStatus.PENDING_SUPPLEMENT);
            obs.setMissingFields(String.join(",", missing));
            observationMapper.insert(obs);
            auditService.record("ICING_OBSERVATION", obs.getId(), "PENDING_SUPPLEMENT",
                    "观测 " + observationNo + " 异常/缺失字段待补：" + obs.getMissingFields(),
                    req.getOperator());
        }
        return obs;
    }

    /**
     * 待补观测补传：仅 PENDING_SUPPLEMENT 可补；补传后按首次上报时的同一参数版本重算。
     * 一次补不全保持待补（revision 递增），全部补齐才参与风险计算。
     */
    @Transactional
    public IcingObservation supplement(Long observationId, IcingSupplementRequest req) {
        IcingObservation obs = observationMapper.selectById(observationId);
        if (obs == null) {
            throw new IllegalArgumentException("观测记录不存在: " + observationId);
        }
        if (obs.getStatus() != IcingObservationStatus.PENDING_SUPPLEMENT) {
            throw new IllegalArgumentException("观测不在待补状态，无需补传: " + obs.getStatus());
        }
        Blade blade = bladeService.mustGet(obs.getBladeId());
        assertValidBlade(blade);

        if (req.getTemperatureC() != null) {
            assertInRange("温度", req.getTemperatureC(), TEMP_MIN, TEMP_MAX);
            obs.setTemperatureC(req.getTemperatureC());
        }
        if (req.getHumidityPct() != null) {
            assertInRange("湿度", req.getHumidityPct(), HUMIDITY_MIN, HUMIDITY_MAX);
            obs.setHumidityPct(req.getHumidityPct());
        }
        if (req.getWindSpeedMs() != null) {
            assertInRange("风速", req.getWindSpeedMs(), WIND_MIN, WIND_MAX);
            obs.setWindSpeedMs(req.getWindSpeedMs());
        }
        if (req.getVibration() != null) {
            assertInRange("历史振动", req.getVibration(), VIBRATION_MIN, Double.MAX_VALUE);
            obs.setVibration(req.getVibration());
        }
        obs.setRevision(obs.getRevision() + 1);

        List<String> missing = findMissing(obs.getTemperatureC(), obs.getHumidityPct(),
                obs.getWindSpeedMs(), obs.getVibration());
        if (missing.isEmpty()) {
            obs.setStatus(IcingObservationStatus.OK);
            obs.setMissingFields(null);
            obs.setSupplementedAt(LocalDateTime.now());
            observationMapper.updateById(obs);
            evaluateAndAlert(obs, req.getOperator());
            auditService.record("ICING_OBSERVATION", obs.getId(), "SUPPLEMENTED",
                    "观测 " + obs.getObservationNo() + " 补齐待补字段，按参数版本 v"
                            + versionNoOf(obs.getParamVersionId()) + " 重算",
                    req.getOperator());
        } else {
            obs.setMissingFields(String.join(",", missing));
            observationMapper.updateById(obs);
        }
        return obs;
    }

    // ---------------- 风险计算与告警 ----------------

    /** 用观测固化的参数版本计算风险，达到阈值才生成告警并通知值班人员 */
    private void evaluateAndAlert(IcingObservation obs, String operator) {
        IcingModelParam param = paramService.getById(obs.getParamVersionId());
        if (param == null) {
            // 极端情况下固化版本缺失，退回当前版本（不会发生：参数版本只退役不删除）
            param = paramService.mustGetCurrent();
        }
        IcingRiskCalculator.Scores scores = calculator.evaluate(param,
                obs.getTemperatureC(), obs.getHumidityPct(), obs.getWindSpeedMs(), obs.getVibration());
        if (scores.getTotalScore() < param.getWarnThreshold()) {
            // 低风险不告警
            return;
        }

        IcingRiskLevel level = scores.getTotalScore() >= param.getStopThreshold()
                ? IcingRiskLevel.HIGH : IcingRiskLevel.MEDIUM;
        IcingAlert alert = new IcingAlert();
        alert.setAlertNo("IA" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet());
        alert.setTurbineId(obs.getTurbineId());
        alert.setBladeId(obs.getBladeId());
        alert.setObservationId(obs.getId());
        alert.setRiskLevel(level);
        alert.setRiskScore(scores.getTotalScore());
        alert.setTempScore(scores.getTempScore());
        alert.setHumidityScore(scores.getHumidityScore());
        alert.setWindScore(scores.getWindScore());
        alert.setVibrationScore(scores.getVibrationScore());
        // 快照计算所用参数版本：历史告警不随新参数重算
        alert.setParamVersionId(param.getId());
        alert.setParamVersionNo(param.getVersionNo());
        alert.setStatus(IcingAlertStatus.ACTIVE);
        if (level == IcingRiskLevel.HIGH) {
            alert.setShutdownAdvice("风险分 " + scores.getTotalScore() + " 达到停机阈值 "
                    + param.getStopThreshold() + "（参数 v" + param.getVersionNo()
                    + "）：建议立即停机等待融冰/除冰，复核叶片气动平衡；停机须经人工确认后执行。");
        }
        save(alert);

        IcingNotifyType notifyType = level == IcingRiskLevel.HIGH
                ? IcingNotifyType.STOP_ADVICE : IcingNotifyType.WARN;
        String content = "风机叶片结冰" + (level == IcingRiskLevel.HIGH ? "高风险，生成停机建议" : "预警")
                + "：风险分 " + scores.getTotalScore() + "（参数 v" + param.getVersionNo()
                + "），温度分 " + scores.getTempScore() + "、湿度分 " + scores.getHumidityScore()
                + "、风速分 " + scores.getWindScore() + "、振动分 " + scores.getVibrationScore()
                + (level == IcingRiskLevel.HIGH ? "。请值班人员确认停机建议。" : "。请持续关注。");
        notificationService.create(alert.getId(), obs.getTurbineId(), obs.getBladeId(),
                notifyType, DUTY_RECIPIENT, content);

        auditService.record("ICING_ALERT", alert.getId(),
                level == IcingRiskLevel.HIGH ? "STOP_ADVICE" : "WARN",
                content, operator);
    }

    private List<String> findMissing(Double temperature, Double humidity, Double wind, Double vibration) {
        Set<String> missing = new LinkedHashSet<>();
        if (temperature == null || outOfRange(temperature, TEMP_MIN, TEMP_MAX)) {
            missing.add("temperatureC");
        }
        if (humidity == null || outOfRange(humidity, HUMIDITY_MIN, HUMIDITY_MAX)) {
            missing.add("humidityPct");
        }
        if (wind == null || outOfRange(wind, WIND_MIN, WIND_MAX)) {
            missing.add("windSpeedMs");
        }
        // 历史振动缺失（传感器历史不可得）同样待补
        if (vibration == null || vibration < VIBRATION_MIN) {
            missing.add("vibration");
        }
        return new ArrayList<>(missing);
    }

    private boolean outOfRange(double v, double min, double max) {
        return v < min || v > max;
    }

    private void assertInRange(String name, double v, double min, double max) {
        if (v < min || v > max) {
            throw new IllegalArgumentException(name + "超出合理区间 [" + min + ", " + max + "]: " + v);
        }
    }

    private int versionNoOf(Long paramVersionId) {
        IcingModelParam param = paramService.getById(paramVersionId);
        return param != null ? param.getVersionNo() : -1;
    }

    // ---------------- 两次人工确认 ----------------

    /**
     * 停机人工确认：系统只生成停机建议，确认后才执行停机。
     * 仅 ACTIVE 的高风险（带停机建议）告警可确认；重复确认拒绝。
     */
    @Transactional
    public IcingAlert confirmShutdown(Long alertId, IcingShutdownConfirmRequest req) {
        IcingAlert alert = mustGetAlert(alertId);
        if (alert.getStatus() != IcingAlertStatus.ACTIVE) {
            throw new IllegalArgumentException("告警不在待确认状态，不能确认停机: " + alert.getStatus());
        }
        if (alert.getRiskLevel() != IcingRiskLevel.HIGH || alert.getShutdownAdvice() == null) {
            throw new IllegalArgumentException("该预警未生成停机建议，不能确认停机");
        }
        alert.setStatus(IcingAlertStatus.STOP_CONFIRMED);
        alert.setShutdownConfirmedBy(req.getConfirmedBy());
        alert.setShutdownConfirmedAt(req.getConfirmedAt() != null ? req.getConfirmedAt() : LocalDateTime.now());
        alert.setShutdownRemark(req.getRemark());
        updateById(alert);

        auditService.record("ICING_ALERT", alert.getId(), "SHUTDOWN_CONFIRMED",
                "人工确认停机建议并执行停机：" + alert.getAlertNo(), req.getConfirmedBy());
        return alert;
    }

    /**
     * 解除风险（再次人工确认）：
     * - HIGH（已生成停机建议）须先确认停机，再次确认才闭环；
     * - MEDIUM（一般预警，未停机）风险解除时人工确认即闭环。
     */
    @Transactional
    public IcingAlert confirmResolve(Long alertId, IcingResolveRequest req) {
        IcingAlert alert = mustGetAlert(alertId);
        if (alert.getStatus() == IcingAlertStatus.RESOLVED) {
            throw new IllegalArgumentException("告警已解除，不能重复确认");
        }
        boolean needShutdownFirst = alert.getRiskLevel() == IcingRiskLevel.HIGH;
        if (needShutdownFirst && alert.getStatus() != IcingAlertStatus.STOP_CONFIRMED) {
            throw new IllegalArgumentException("高风险预警须先确认停机，才能确认解除风险: " + alert.getStatus());
        }
        if (!needShutdownFirst && alert.getStatus() != IcingAlertStatus.ACTIVE) {
            throw new IllegalArgumentException("告警状态不允许解除: " + alert.getStatus());
        }
        alert.setStatus(IcingAlertStatus.RESOLVED);
        alert.setResolvedBy(req.getResolvedBy());
        alert.setResolvedAt(req.getResolvedAt() != null ? req.getResolvedAt() : LocalDateTime.now());
        alert.setResolveRemark(req.getRemark());
        updateById(alert);

        auditService.record("ICING_ALERT", alert.getId(), "RESOLVED",
                needShutdownFirst ? "停机后再次人工确认，风险解除：" + alert.getAlertNo()
                        : "人工确认风险解除：" + alert.getAlertNo(),
                req.getResolvedBy());
        return alert;
    }

    // ---------------- 查询（页面 / 导出 / 后台同口径） ----------------

    /**
     * 叶片结冰风险预警列表（页面、导出、后台共用此方法）。
     * 同口径：只统计有效叶片（blade.valid = TRUE）；无效叶片的历史告警一律不出现。
     *
     * @param status    可选状态过滤
     * @param turbineId 可选风机过滤
     */
    public List<IcingWarningRow> listWarningRows(IcingAlertStatus status, Long turbineId) {
        List<IcingAlert> alerts = list(new QueryWrapper<IcingAlert>()
                .eq(status != null, "status", status)
                .eq(turbineId != null, "turbine_id", turbineId)
                .orderByDesc("id"));
        if (alerts.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> bladeIds = alerts.stream().map(IcingAlert::getBladeId).distinct().collect(Collectors.toList());
        Map<Long, Blade> bladeMap = bladeService.listByIds(bladeIds).stream()
                .collect(Collectors.toMap(Blade::getId, b -> b));
        Set<Long> turbineIds = alerts.stream().map(IcingAlert::getTurbineId).collect(Collectors.toSet());
        Map<Long, Turbine> turbineMap = turbineService.listByIds(turbineIds).stream()
                .collect(Collectors.toMap(Turbine::getId, t -> t));
        Set<Long> observationIds = alerts.stream().map(IcingAlert::getObservationId).collect(Collectors.toSet());
        Map<Long, IcingObservation> obsMap = observationMapper.selectBatchIds(observationIds).stream()
                .collect(Collectors.toMap(IcingObservation::getId, o -> o));

        List<IcingWarningRow> rows = new ArrayList<>();
        for (IcingAlert alert : alerts) {
            Blade blade = bladeMap.get(alert.getBladeId());
            // 同口径核心：无效叶片不进入预警页面/导出/后台
            if (blade == null || !Boolean.TRUE.equals(blade.getValid())) {
                continue;
            }
            IcingWarningRow row = new IcingWarningRow();
            row.setAlert(alert);
            row.setBladeNo(blade.getBladeNo());
            Turbine turbine = turbineMap.get(alert.getTurbineId());
            row.setTurbineNo(turbine != null ? turbine.getTurbineNo() : String.valueOf(alert.getTurbineId()));
            IcingObservation obs = obsMap.get(alert.getObservationId());
            row.setObservedAt(obs != null ? obs.getObservedAt() : null);
            rows.add(row);
        }
        return rows;
    }

    /** 待补观测列表（可按叶片过滤） */
    public List<IcingObservation> listObservations(Long bladeId, IcingObservationStatus status) {
        return observationMapper.selectList(new QueryWrapper<IcingObservation>()
                .eq(bladeId != null, "blade_id", bladeId)
                .eq(status != null, "status", status)
                .orderByDesc("id"));
    }

    /** 告警详情：告警 + 观测 + 通知回执 */
    public Map<String, Object> alertDetail(Long alertId) {
        IcingAlert alert = mustGetAlert(alertId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("alert", alert);
        detail.put("observation", observationMapper.selectById(alert.getObservationId()));
        detail.put("notifications", notificationService.listByAlert(alertId));
        return detail;
    }

    public IcingAlert mustGetAlert(Long id) {
        IcingAlert alert = getById(id);
        if (alert == null) {
            throw new IllegalArgumentException("结冰预警不存在: " + id);
        }
        return alert;
    }

    public List<IcingNotification> listNotifications(Long alertId) {
        return notificationService.listByAlert(alertId);
    }

    private void assertValidBlade(Blade blade) {
        if (!Boolean.TRUE.equals(blade.getValid())) {
            throw new IllegalArgumentException("叶片已置为无效，不再参与结冰预警: " + blade.getBladeNo());
        }
    }

    private String uniqueNo(String preferred, String prefix) {
        String no = preferred != null && !preferred.trim().isEmpty()
                ? preferred : prefix + System.currentTimeMillis() + "-" + SEQ.incrementAndGet();
        return no;
    }

    // 供导出/页面复用的列定义，保证三处同口径同字段
    public static final List<String> CSV_HEADERS = Arrays.asList(
            "预警编号", "风机编号", "叶片编号", "观测时刻", "风险等级", "风险分",
            "温度分", "湿度分", "风速分", "振动分", "参数版本", "状态",
            "停机建议", "停机确认人", "停机确认时间", "解除确认人", "解除时间");
}
