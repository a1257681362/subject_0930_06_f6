package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.PartDisposition;
import com.evops.enums.PartType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 换件记录：保留旧件去向与新件状态的前后关系。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_part_replacement")
public class PartReplacement extends BaseEntity {
    private Long turbineId;
    private Long defectId;
    private Long requisitionId;
    private PartType partType;
    /** 旧件（无在装旧件时为空） */
    private Long oldPartId;
    private String oldSerialNo;
    /** 旧件去向 */
    private PartDisposition oldDisposition;
    private Long newPartId;
    private String newSerialNo;
    /** 新件状态（装机后为 INSTALLED） */
    private String newPartStatus;
    private LocalDateTime replacedAt;
    /** 换件时所用规则版本 */
    private Long ruleVersionId;
    /** 序列追踪结论 */
    private String conclusion;
    private String operator;
}
