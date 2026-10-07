package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.VersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 结冰风险模型参数（版本化）。
 * 调整只生成新版本并发布，旧版本退役但内容不改；告警固化引用计算时的参数版本，不随新参数重算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_model_param")
public class IcingModelParam extends BaseEntity {
    private Integer versionNo;
    private Double tempWeight;
    private Double humidityWeight;
    private Double windWeight;
    private Double vibrationWeight;
    /** 结冰敏感温度上限（℃） */
    private Double tempCutoffC;
    private Double humidityLowPct;
    private Double humidityHighPct;
    private Double windLowMs;
    private Double windHighMs;
    private Double vibrationLow;
    private Double vibrationHigh;
    /** 历史振动回看窗口（小时） */
    private Integer vibrationWindowHours;
    /** 一般预警阈值（含） */
    private Integer warnThreshold;
    /** 停机建议阈值（含） */
    private Integer stopThreshold;
    private VersionStatus status;
    private LocalDateTime publishedAt;
}
