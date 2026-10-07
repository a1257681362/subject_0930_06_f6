package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 发布前校验入参：现场填报的当前气象与电量条件 */
@Data
public class PublishCheckRequest {
    /** 当前风速（m/s） */
    @NotNull(message = "当前风速不能为空")
    private Double windSpeed;
    /** 当前电量（%） */
    @NotNull(message = "当前电量不能为空")
    @Min(value = 0, message = "电量需在 0-100 之间")
    @Max(value = 100, message = "电量需在 0-100 之间")
    private Integer batteryPct;
}
