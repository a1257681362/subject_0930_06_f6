package com.evops.dto;

import com.evops.entity.IcingAlert;
import com.evops.entity.IcingAlertConfirmation;
import com.evops.entity.IcingNotification;
import lombok.Data;

import java.util.List;

/** 结冰告警详情：告警本体 + 两次人工确认记录 + 值班通知回执 */
@Data
public class IcingAlertDetail {
    private IcingAlert alert;
    private String turbineNo;
    private String site;
    private List<IcingAlertConfirmation> confirmations;
    private List<IcingNotification> notifications;
}
