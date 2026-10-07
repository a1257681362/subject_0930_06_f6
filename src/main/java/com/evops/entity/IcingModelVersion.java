package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.VersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 叶片结冰风险模型参数（版本化）。
 * 参数调整只生成新版本并发布，旧版本退役但内容不改；告警固化产生时的版本，不随新参数重算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_model_version")
public class IcingModelVersion extends BaseEntity {
    private Integer versionNo;

    /** 温度起算阈值（℃）：温度低于该值开始计温度风险分 */
    private Double tempThreshold;
    /** 温度满分带宽（℃）：从阈值再低该带宽达到温度满分 */
    private Double tempFullBandC;
    private Integer tempScoreMax;

    /** 湿度起算阈值（%）：湿度高于该值开始计湿度风险分 */
    private Double humidityThreshold;
    /** 湿度满分带宽（百分点）：从阈值再高该带宽达到湿度满分 */
    private Double humidityFullBandPct;
    private Integer humidityScoreMax;

    /** 适宜风速下限（m/s）：低于该值判为易结冰（静稳） */
    private Double windSpeedMin;
    /** 适宜风速上限（m/s）：高于该值判为易结冰（强风携冻雨） */
    private Double windSpeedMax;
    private Integer windScoreMax;

    /** 历史振动基线（g） */
    private Double vibrationBaseline;
    /** 振动异常偏离比例：偏离达到该比例计振动满分 */
    private Double vibrationAbnormalRatio;
    private Integer vibrationScoreMax;

    /** 告警阈值：总分 >= 该值生成告警与停机建议 */
    private Integer alertThreshold;
    /** 危急阈值：总分 >= 该值风险等级为危急 */
    private Integer criticalThreshold;
    private String shutdownAdvice;

    private VersionStatus status;
    private LocalDateTime publishedAt;
}
