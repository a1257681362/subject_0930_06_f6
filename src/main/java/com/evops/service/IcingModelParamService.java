package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.IcingModelParamRequest;
import com.evops.entity.IcingModelParam;
import com.evops.enums.VersionStatus;
import com.evops.mapper.IcingModelParamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结冰风险模型参数：版本化发布。
 * 调整只生成新版本，旧版本退役但内容不改；历史告警与待补观测继续引用各自的旧版本，不随新参数重算。
 */
@Service
public class IcingModelParamService extends ServiceImpl<IcingModelParamMapper, IcingModelParam> {

    private final AuditService auditService;

    public IcingModelParamService(AuditService auditService) {
        this.auditService = auditService;
    }

    /** 当前发布参数版本 */
    public IcingModelParam mustGetCurrent() {
        IcingModelParam param = getOne(new QueryWrapper<IcingModelParam>()
                .eq("status", VersionStatus.PUBLISHED)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        if (param == null) {
            throw new IllegalArgumentException("未配置结冰风险模型参数");
        }
        return param;
    }

    /** 全量版本（含已退役），旧版本内容可查 */
    public List<IcingModelParam> listVersions() {
        return list(new QueryWrapper<IcingModelParam>().orderByAsc("version_no"));
    }

    /**
     * 参数调整：生成新版本（版本号递增并发布），旧版本退役但内容不改。
     * 已产生的告警固化引用旧版本，不随调整重算。
     */
    @Transactional
    public IcingModelParam adjust(IcingModelParamRequest req) {
        validate(req);
        IcingModelParam current = mustGetCurrent();

        IcingModelParam next = new IcingModelParam();
        next.setVersionNo(current.getVersionNo() + 1);
        copy(req, next);
        next.setStatus(VersionStatus.PUBLISHED);
        next.setPublishedAt(LocalDateTime.now());
        save(next);

        // 旧版本仅退役，内容保持不动
        current.setStatus(VersionStatus.RETIRED);
        updateById(current);

        auditService.record("ICING_MODEL_PARAM", next.getId(), "ADJUST_PARAM",
                "结冰模型参数调整生成 v" + next.getVersionNo() + "，v" + current.getVersionNo()
                        + " 退役，历史告警不随新参数重算",
                req.getOperator());
        return next;
    }

    private void validate(IcingModelParamRequest req) {
        double weightSum = req.getTempWeight() + req.getHumidityWeight()
                + req.getWindWeight() + req.getVibrationWeight();
        if (Math.abs(weightSum - 1.0) > 0.0001) {
            throw new IllegalArgumentException("四项权重之和必须为 1，当前为 " + weightSum);
        }
        if (req.getHumidityLowPct() >= req.getHumidityHighPct()
                || req.getWindLowMs() >= req.getWindHighMs()
                || req.getVibrationLow() >= req.getVibrationHigh()) {
            throw new IllegalArgumentException("起算值必须小于饱和值");
        }
        if (req.getWarnThreshold() > req.getStopThreshold()
                || req.getWarnThreshold() <= 0 || req.getWarnThreshold() > 100
                || req.getStopThreshold() <= 0 || req.getStopThreshold() > 100) {
            throw new IllegalArgumentException("预警阈值不得高于停机阈值，且均须在 (0,100]");
        }
        if (req.getVibrationWindowHours() <= 0) {
            throw new IllegalArgumentException("历史振动回看窗口必须为正整数小时");
        }
    }

    private void copy(IcingModelParamRequest req, IcingModelParam p) {
        p.setTempWeight(req.getTempWeight());
        p.setHumidityWeight(req.getHumidityWeight());
        p.setWindWeight(req.getWindWeight());
        p.setVibrationWeight(req.getVibrationWeight());
        p.setTempCutoffC(req.getTempCutoffC());
        p.setHumidityLowPct(req.getHumidityLowPct());
        p.setHumidityHighPct(req.getHumidityHighPct());
        p.setWindLowMs(req.getWindLowMs());
        p.setWindHighMs(req.getWindHighMs());
        p.setVibrationLow(req.getVibrationLow());
        p.setVibrationHigh(req.getVibrationHigh());
        p.setVibrationWindowHours(req.getVibrationWindowHours());
        p.setWarnThreshold(req.getWarnThreshold());
        p.setStopThreshold(req.getStopThreshold());
    }
}
