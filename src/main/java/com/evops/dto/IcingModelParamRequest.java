package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 模型参数调整：只生成新版本（版本号递增并发布），旧版本退役但内容不改。
 * 已产生的告警继续引用旧版本，不随调整重算。
 */
@Data
public class IcingModelParamRequest {
    @NotNull(message = "温度权重不能为空")
    private Double tempWeight;
    @NotNull(message = "湿度权重不能为空")
    private Double humidityWeight;
    @NotNull(message = "风速权重不能为空")
    private Double windWeight;
    @NotNull(message = "历史振动权重不能为空")
    private Double vibrationWeight;
    @NotNull(message = "结冰敏感温度上限不能为空")
    private Double tempCutoffC;
    @NotNull(message = "湿度起算值不能为空")
    private Double humidityLowPct;
    @NotNull(message = "湿度饱和值不能为空")
    private Double humidityHighPct;
    @NotNull(message = "风速起算值不能为空")
    private Double windLowMs;
    @NotNull(message = "风速饱和值不能为空")
    private Double windHighMs;
    @NotNull(message = "振动起算值不能为空")
    private Double vibrationLow;
    @NotNull(message = "振动饱和值不能为空")
    private Double vibrationHigh;
    @NotNull(message = "历史振动回看窗口不能为空")
    private Integer vibrationWindowHours;
    @NotNull(message = "一般预警阈值不能为空")
    private Integer warnThreshold;
    @NotNull(message = "停机阈值不能为空")
    private Integer stopThreshold;
    @NotNull(message = "操作人不能为空")
    private String operator;
}
