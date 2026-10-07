package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 待补观测补传：仅补缺失/异常字段；补传后按该观测生成时的同一参数版本重算风险。
 * 观测编号与首次观测时刻保留，revision 递增。
 */
@Data
public class IcingSupplementRequest {
    private Double temperatureC;
    private Double humidityPct;
    private Double windSpeedMs;
    private Double vibration;
    @NotBlank(message = "补传人不能为空")
    private String operator;
}
