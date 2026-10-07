package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.RequisitionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 维修领用单：绑定风机与缺陷单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_part_requisition")
public class PartRequisition extends BaseEntity {
    private String reqNo;
    private Long turbineId;
    private Long defectId;
    private Long partId;
    private String issuedBy;
    private LocalDateTime issuedAt;
    private String purpose;
    private RequisitionStatus status;
}
