package com.evops.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/** 航线新版本入参 */
@Data
public class RouteVersionRequest {
    @NotEmpty(message = "航点不能为空")
    @Size(min = 2, message = "航线至少需要 2 个航点")
    @Valid
    private List<WaypointDto> waypoints;
    /** 预计耗电（%） */
    private Integer requiredBatteryPct;
    /** 允许作业的最大风速（m/s） */
    private Double maxWindSpeed;
}
