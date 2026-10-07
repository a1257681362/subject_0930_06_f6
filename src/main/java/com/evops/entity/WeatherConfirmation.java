package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.ConfirmationResult;
import com.evops.enums.LightningRisk;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 作业前二次确认：开工前对当前天气的实测确认。
 * 每次确认一条，开工以最近一次 PASS 为准。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_weather_confirmation")
public class WeatherConfirmation extends BaseEntity {
    private Long shiftId;
    private Double windSpeed;
    private Double rainfallMm;
    private LightningRisk lightningRisk;
    private ConfirmationResult result;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
}
