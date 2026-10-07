package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计事件：航线规划与禁飞区的关键动作留痕（创建/发布/改绑/改线审批/划定/取消），
 * 支撑航线规划与禁飞区全程可追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_audit_event")
public class AuditEvent extends BaseEntity {
    /** 对象类型：ROUTE / VERSION / TASK / ZONE / CHANGE */
    private String entityType;
    private Long entityId;
    /** 动作：如 ROUTE_CREATE、VERSION_PUBLISH、TASK_REBIND、ZONE_CANCEL */
    private String action;
    /** 明细 JSON */
    private String detail;
    private String operator;
}
