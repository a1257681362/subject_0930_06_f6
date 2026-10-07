package com.evops.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/** 临时改线申请入参 */
@Data
public class RouteChangeCreateRequest {
    /** 申请编号；为空时自动生成 */
    private String changeNo;
    @NotNull(message = "航线不能为空")
    private Long routeId;
    /** 改线基准版本；为空时取当前发布版本 */
    private Long baseVersionId;
    @NotBlank(message = "改线原因不能为空")
    private String reason;
    @NotEmpty(message = "拟改航点不能为空")
    @Size(min = 2, message = "航线至少需要 2 个航点")
    @Valid
    private List<WaypointDto> proposedWaypoints;
    /** 拟改预计耗电（%）；为空沿用基准版本 */
    private Integer proposedRequiredBatteryPct;
    /** 拟改最大作业风速（m/s）；为空沿用基准版本 */
    private Double proposedMaxWindSpeed;
}
