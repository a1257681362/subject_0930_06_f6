package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.ReturnStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 退库单：退库须经验收，验收通过才回到在库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_part_return")
public class PartReturn extends BaseEntity {
    private String returnNo;
    private Long partId;
    private Long requisitionId;
    private String returnedBy;
    private LocalDateTime returnedAt;
    private ReturnStatus status;
    private String acceptedBy;
    private LocalDateTime acceptedAt;
    private String acceptRemark;
}
