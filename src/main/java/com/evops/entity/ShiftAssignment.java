package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.AssignType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 班次人员/装备分配 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_shift_assignment")
public class ShiftAssignment extends BaseEntity {
    private Long shiftId;
    private AssignType assignType;
    private Long workerId;
    private Long equipmentId;
}
