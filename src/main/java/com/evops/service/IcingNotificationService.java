package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.entity.IcingNotification;
import com.evops.enums.IcingNotifyType;
import com.evops.mapper.IcingNotificationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 结冰预警通知回执：达到阈值通知值班人员，统一留痕。
 * 与告警写入同事务，保证业务落库则回执必在。
 */
@Service
public class IcingNotificationService extends ServiceImpl<IcingNotificationMapper, IcingNotification> {

    private static final AtomicLong RECEIPT_SEQ = new AtomicLong();

    public IcingNotification create(Long alertId, Long turbineId, Long bladeId,
                                    IcingNotifyType type, String recipient, String content) {
        IcingNotification notification = new IcingNotification();
        notification.setAlertId(alertId);
        notification.setTurbineId(turbineId);
        notification.setBladeId(bladeId);
        notification.setNotifyType(type);
        notification.setRecipient(recipient);
        notification.setContent(content);
        notification.setReceiptNo("IC" + System.currentTimeMillis() + "-" + RECEIPT_SEQ.incrementAndGet());
        notification.setSentAt(LocalDateTime.now());
        save(notification);
        return notification;
    }

    public List<IcingNotification> listByAlert(Long alertId) {
        return list(new QueryWrapper<IcingNotification>()
                .eq("alert_id", alertId)
                .orderByAsc("id"));
    }
}
