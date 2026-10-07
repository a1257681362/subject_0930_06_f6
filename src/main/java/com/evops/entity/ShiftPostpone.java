package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 延期记录：每次延期一条，保留新旧计划并关联通知回执。
 * 原计划由 t_work_shift.original_start/original_end 固化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_shift_postpone")
public class ShiftPostpone extends BaseEntity {
    private Long shiftId;
    private LocalDateTime oldStart;
    private LocalDateTime oldEnd;
    private LocalDateTime newStart;
    private LocalDateTime newEnd;
    private String reason;
    /** 本次延期的通知回执 */
    private Long notificationId;
    private String postponedBy;
    private LocalDateTime postponedAt;
}
