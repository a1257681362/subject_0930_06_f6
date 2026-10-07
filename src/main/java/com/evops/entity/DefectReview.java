package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 专家复核记录：可升级或降级，但不覆盖初判（t_defect.initial_level 不变）。
 * 每次复核留痕：from_level -> to_level、意见、责任人。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_review")
public class DefectReview extends BaseEntity {
    private Long defectId;
    private String reviewer;
    private String fromLevel;
    private String toLevel;
    /** 复核意见 */
    private String opinion;
    /** 本次复核记录的责任人 */
    private String responsiblePerson;
    private LocalDateTime reviewedAt;
}
