package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.ApprovalDecision;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 改线审批记录：每级一条（changeId + stepNo 唯一）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_route_change_approval")
public class RouteChangeApproval extends BaseEntity {
    private Long changeId;
    /** 审批级次：1 场站、2 运检中心 */
    private Integer stepNo;
    private String approver;
    private ApprovalDecision decision;
    /** 审批意见 */
    private String opinion;
    private LocalDateTime decidedAt;
}
