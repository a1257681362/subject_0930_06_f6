package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.NotifyType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知回执：窗口收窄预警 / 延期通知。
 * 冗余 taskId 便于追溯（候选班次重建后回执仍在）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_shift_notification")
public class ShiftNotification extends BaseEntity {
    private Long shiftId;
    private Long taskId;
    private NotifyType notifyType;
    private String recipient;
    private String content;
    /** 回执编号，全局唯一 */
    private String receiptNo;
    private LocalDateTime sentAt;
}
