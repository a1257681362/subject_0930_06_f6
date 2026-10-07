package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.IcingModelAdjustRequest;
import com.evops.entity.IcingModelVersion;
import com.evops.enums.VersionStatus;
import com.evops.mapper.IcingModelVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结冰风险模型参数版本管理。
 * 调整只生成新版本（版本号递增并发布），旧版本退役但内容不改；
 * 已产生的告警固化产生时的版本，不随参数调整重算。
 */
@Service
public class IcingModelService extends ServiceImpl<IcingModelVersionMapper, IcingModelVersion> {

    private final AuditService auditService;

    public IcingModelService(AuditService auditService) {
        this.auditService = auditService;
    }

    /** 当前发布版本（版本号最大的 PUBLISHED） */
    public IcingModelVersion mustGetCurrent() {
        IcingModelVersion model = getOne(new QueryWrapper<IcingModelVersion>()
                .eq("status", VersionStatus.PUBLISHED)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        if (model == null) {
            throw new IllegalArgumentException("未配置结冰风险模型参数");
        }
        return model;
    }

    /** 全量版本（含已退役），旧版本内容可查 */
    public List<IcingModelVersion> listVersions() {
        return list(new QueryWrapper<IcingModelVersion>().orderByAsc("version_no"));
    }

    public IcingModelVersion mustGetById(Long id) {
        IcingModelVersion model = getById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型版本不存在: " + id);
        }
        return model;
    }

    /**
     * 参数调整：生成新版本并发布，旧版本退役但内容不改。
     * 历史告警继续引用旧版本，不随调整重算。
     */
    @Transactional
    public IcingModelVersion adjust(IcingModelAdjustRequest req) {
        validate(req);
        IcingModelVersion current = mustGetCurrent();

        IcingModelVersion next = new IcingModelVersion();
        next.setVersionNo(current.getVersionNo() + 1);
        copyParams(req, next);
        next.setStatus(VersionStatus.PUBLISHED);
        next.setPublishedAt(LocalDateTime.now());
        save(next);

        // 旧版本仅退役，内容保持不动
        current.setStatus(VersionStatus.RETIRED);
        updateById(current);

        auditService.record("ICING_MODEL", next.getId(), "ADJUST_MODEL",
                "结冰模型参数调整生成 v" + next.getVersionNo() + "，v" + current.getVersionNo()
                        + " 退役，历史告警不重算",
                req.getOperator());
        return next;
    }

    private void validate(IcingModelAdjustRequest req) {
        if (req.getTempFullBandC() <= 0 || req.getHumidityFullBandPct() <= 0
                || req.getVibrationAbnormalRatio() <= 0) {
            throw new IllegalArgumentException("满分带宽/异常比例必须为正数");
        }
        if (req.getTempScoreMax() < 0 || req.getHumidityScoreMax() < 0
                || req.getWindScoreMax() < 0 || req.getVibrationScoreMax() < 0) {
            throw new IllegalArgumentException("各因子满分不能为负");
        }
        if (req.getWindSpeedMin() >= req.getWindSpeedMax()) {
            throw new IllegalArgumentException("适宜风速下限必须小于上限");
        }
        if (req.getAlertThreshold() > req.getCriticalThreshold()) {
            throw new IllegalArgumentException("告警阈值不能高于危急阈值");
        }
        if (req.getCriticalThreshold() > 100 || req.getAlertThreshold() < 0) {
            throw new IllegalArgumentException("风险阈值应落在 0~100");
        }
    }

    private void copyParams(IcingModelAdjustRequest req, IcingModelVersion m) {
        m.setTempThreshold(req.getTempThreshold());
        m.setTempFullBandC(req.getTempFullBandC());
        m.setTempScoreMax(req.getTempScoreMax());
        m.setHumidityThreshold(req.getHumidityThreshold());
        m.setHumidityFullBandPct(req.getHumidityFullBandPct());
        m.setHumidityScoreMax(req.getHumidityScoreMax());
        m.setWindSpeedMin(req.getWindSpeedMin());
        m.setWindSpeedMax(req.getWindSpeedMax());
        m.setWindScoreMax(req.getWindScoreMax());
        m.setVibrationBaseline(req.getVibrationBaseline());
        m.setVibrationAbnormalRatio(req.getVibrationAbnormalRatio());
        m.setVibrationScoreMax(req.getVibrationScoreMax());
        m.setAlertThreshold(req.getAlertThreshold());
        m.setCriticalThreshold(req.getCriticalThreshold());
        m.setShutdownAdvice(req.getShutdownAdvice());
    }
}
