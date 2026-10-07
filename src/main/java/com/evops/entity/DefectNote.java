package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 追加说明：已关闭缺陷唯一允许的写操作；只增不改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_note")
public class DefectNote extends BaseEntity {
    private Long defectId;
    private String note;
    private String notedBy;
    private LocalDateTime notedAt;
}
