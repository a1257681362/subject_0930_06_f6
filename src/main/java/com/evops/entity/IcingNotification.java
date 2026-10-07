package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.IcingNotifyType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 结冰预警通知回执：达到阈值通知值班人员。
 * 与告警写入同事务，业务落库则回执必在；回执编号唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_notification")
public class IcingNotification extends BaseEntity {
    private Long alertId;
    private Long turbineId;
    private Long bladeId;
    private IcingNotifyType notifyType;
    private String recipient;
    private String content;
    private String receiptNo;
    private LocalDateTime sentAt;
}
