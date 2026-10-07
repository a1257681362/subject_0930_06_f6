package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.entity.ShiftNotification;
import com.evops.enums.NotifyType;
import com.evops.mapper.ShiftNotificationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通知回执：窗口收窄预警 / 延期通知统一留痕。
 * 与业务写入同事务，保证业务落库则回执必在。
 */
@Service
public class ShiftNotificationService extends ServiceImpl<ShiftNotificationMapper, ShiftNotification> {

    private static final AtomicLong RECEIPT_SEQ = new AtomicLong();

    /** 生成通知与回执编号 */
    public ShiftNotification create(Long shiftId, Long taskId, NotifyType type, String recipient, String content) {
        ShiftNotification notification = new ShiftNotification();
        notification.setShiftId(shiftId);
        notification.setTaskId(taskId);
        notification.setNotifyType(type);
        notification.setRecipient(recipient);
        notification.setContent(content);
        notification.setReceiptNo("RC" + System.currentTimeMillis() + "-" + RECEIPT_SEQ.incrementAndGet());
        notification.setSentAt(LocalDateTime.now());
        save(notification);
        return notification;
    }

    /** 班次的全部通知回执 */
    public List<ShiftNotification> listByShift(Long shiftId) {
        return list(new QueryWrapper<ShiftNotification>()
                .eq("shift_id", shiftId)
                .orderByAsc("id"));
    }

    /** 任务的全部通知回执（含已重建候选班次的预警） */
    public List<ShiftNotification> listByTask(Long taskId) {
        return list(new QueryWrapper<ShiftNotification>()
                .eq("task_id", taskId)
                .orderByAsc("id"));
    }
}
