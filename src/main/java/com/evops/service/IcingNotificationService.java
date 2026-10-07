package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.entity.IcingNotification;
import com.evops.mapper.IcingNotificationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 结冰预警通知回执：达到阈值时通知值班人员。
 * 与告警写入同事务，保证告警落库则回执必在。
 */
@Service
public class IcingNotificationService extends ServiceImpl<IcingNotificationMapper, IcingNotification> {

    private static final AtomicLong RECEIPT_SEQ = new AtomicLong();

    /** 值班通知接收人（联调期固定值班岗位，登录体系接入后从排班取当班人员） */
    public static final String DUTY_OFFICER = "值班调度员";

    /** 生成通知与回执编号 */
    public IcingNotification notify(Long alertId, Long turbineId, String recipient, String content) {
        IcingNotification notification = new IcingNotification();
        notification.setAlertId(alertId);
        notification.setTurbineId(turbineId);
        notification.setRecipient(recipient);
        notification.setContent(content);
        notification.setReceiptNo("IC" + System.currentTimeMillis() + "-" + RECEIPT_SEQ.incrementAndGet());
        notification.setSentAt(LocalDateTime.now());
        save(notification);
        return notification;
    }

    /** 告警的全部通知回执 */
    public List<IcingNotification> listByAlert(Long alertId) {
        return list(new QueryWrapper<IcingNotification>()
                .eq("alert_id", alertId)
                .orderByAsc("id"));
    }
}
