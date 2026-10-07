package com.evops.dto;

import com.evops.enums.LightningRisk;
import lombok.Data;

import javax.validation.constraints.NotNull;

/** 作业前二次确认入参：开工前对当前天气的实测确认 */
@Data
public class ConfirmWeatherRequest {
    @NotNull(message = "当前风速不能为空")
    private Double windSpeed;
    @NotNull(message = "当前降雨量不能为空")
    private Double rainfallMm;
    @NotNull(message = "雷电风险不能为空")
    private LightningRisk lightningRisk;
    @NotNull(message = "确认人不能为空")
    private String confirmedBy;
}
