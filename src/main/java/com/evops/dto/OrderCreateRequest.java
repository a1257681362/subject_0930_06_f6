package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 巡检工单创建入参 */
@Data
public class OrderCreateRequest {
    /** 工单号；为空时自动生成 */
    private String orderNo;
    @NotNull(message = "风机不能为空")
    private Long turbineId;
    /** 指定单叶片巡检；为空表示整机巡检 */
    private Long bladeId;
    /** 关联飞行任务；设置后自动带出任务绑定的航线版本（任务须已起飞） */
    private Long flightTaskId;
    /** 直接指定航线版本（未走飞行任务时） */
    private Long routeVersionId;
}
