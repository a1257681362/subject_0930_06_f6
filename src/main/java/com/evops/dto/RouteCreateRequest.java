package com.evops.dto;

import com.evops.enums.BladePosition;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/** 航线创建入参：按 风机+叶片方位+拍摄距离 配置，同时录入首版本航点 */
@Data
public class RouteCreateRequest {
    /** 航线编号；为空时自动生成 */
    private String routeNo;
    @NotNull(message = "风机不能为空")
    private Long turbineId;
    @NotNull(message = "叶片方位不能为空")
    private BladePosition bladePosition;
    @NotNull(message = "拍摄距离不能为空")
    private Double shootDistanceM;
    private String name;
    @NotEmpty(message = "航点不能为空")
    @Size(min = 2, message = "航线至少需要 2 个航点")
    @Valid
    private List<WaypointDto> waypoints;
    /** 预计耗电（%），发布前校验电量余量的基准 */
    private Integer requiredBatteryPct;
    /** 允许作业的最大风速（m/s） */
    private Double maxWindSpeed;
}
