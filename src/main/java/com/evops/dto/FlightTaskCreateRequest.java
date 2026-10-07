package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 飞行任务创建入参 */
@Data
public class FlightTaskCreateRequest {
    /** 任务编号；为空时自动生成 */
    private String taskNo;
    @NotNull(message = "航线不能为空")
    private Long routeId;
    private LocalDateTime plannedTakeoffAt;
}
