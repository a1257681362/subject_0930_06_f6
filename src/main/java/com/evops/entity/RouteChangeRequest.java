package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.dto.WaypointDto;
import com.evops.enums.ChangeStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 临时改线申请：基于 baseVersion 提出新航点串，diffJson 留存版本差异。
 * 两级审批：currentStep 1=场站 2=运检中心；只完成一半时保持 PENDING 停留待处理。
 * 终审通过后生成新航线版本并发布，resultingVersionId 记录落地版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_route_change_request")
public class RouteChangeRequest extends BaseEntity {
    /** 申请编号，全局唯一 */
    private String changeNo;
    private Long routeId;
    /** 改线基准版本 */
    private Long baseVersionId;
    /** 改线原因（如避让施工塔吊） */
    private String reason;
    /** 拟改航点串 JSON */
    private String proposedWaypointsJson;
    /** 拟改预计耗电（%）；null 时沿用基准版本 */
    private Integer proposedRequiredBatteryPct;
    /** 拟改最大作业风速（m/s）；null 时沿用基准版本 */
    private Double proposedMaxWindSpeed;
    /** 与基准版本的差异 JSON：added/removed/moved/paramChanges */
    private String diffJson;
    private ChangeStatus status;
    /** 当前待审批级次：1 场站、2 运检中心 */
    private Integer currentStep;
    /** 终审通过后生成的航线版本 */
    private Long resultingVersionId;

    /** 非持久化：解析后的拟改航点串 */
    @TableField(exist = false)
    private List<WaypointDto> proposedWaypoints;
}
