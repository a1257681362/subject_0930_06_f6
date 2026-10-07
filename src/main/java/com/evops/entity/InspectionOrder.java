package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.OrderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡检工单：关联无人机影像；缺陷、维修、验收挂在工单下推进。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_inspection_order")
public class InspectionOrder extends BaseEntity {
    private String orderNo;
    private Long turbineId;
    /** 指定单叶片巡检；null 表示整机巡检 */
    private Long bladeId;
    private LocalDateTime inspectTime;
    /** 无人机影像地址 */
    private String droneImageUrl;
    /** 关联飞行任务 */
    private Long flightTaskId;
    /** 拍摄时使用的航线版本：历史影像始终引用原航线版本，不随新版本发布变化 */
    private Long routeVersionId;
    private OrderStatus status;
}
