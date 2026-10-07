package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 结冰风险模型参数调整入参：只生成新版本并发布，旧版本退役但内容不改。
 * 历史告警继续引用产生时的旧版本，不随本次调整重算。
 */
@Data
public class IcingModelAdjustRequest {
    @NotNull(message = "温度阈值不能为空")
    private Double tempThreshold;
    @NotNull(message = "温度满分带宽不能为空")
    private Double tempFullBandC;
    @NotNull(message = "温度满分不能为空")
    private Integer tempScoreMax;

    @NotNull(message = "湿度阈值不能为空")
    private Double humidityThreshold;
    @NotNull(message = "湿度满分带宽不能为空")
    private Double humidityFullBandPct;
    @NotNull(message = "湿度满分不能为空")
    private Integer humidityScoreMax;

    @NotNull(message = "风速下限不能为空")
    private Double windSpeedMin;
    @NotNull(message = "风速上限不能为空")
    private Double windSpeedMax;
    @NotNull(message = "风速满分不能为空")
    private Integer windScoreMax;

    @NotNull(message = "振动基线不能为空")
    private Double vibrationBaseline;
    @NotNull(message = "振动异常比例不能为空")
    private Double vibrationAbnormalRatio;
    @NotNull(message = "振动满分不能为空")
    private Integer vibrationScoreMax;

    @NotNull(message = "告警阈值不能为空")
    private Integer alertThreshold;
    @NotNull(message = "危急阈值不能为空")
    private Integer criticalThreshold;
    @NotBlank(message = "停机建议模板不能为空")
    private String shutdownAdvice;
    @NotBlank(message = "操作人不能为空")
    private String operator;
}
