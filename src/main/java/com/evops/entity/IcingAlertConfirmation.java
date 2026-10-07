package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.ConfirmationResult;
import com.evops.enums.IcingConfirmType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 结冰告警人工确认记录：停机确认、解除风险确认（含驳回）逐条留痕 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_alert_confirmation")
public class IcingAlertConfirmation extends BaseEntity {
    private Long alertId;
    private IcingConfirmType confirmType;
    private ConfirmationResult decision;
    private String confirmer;
    private String opinion;
    private LocalDateTime confirmedAt;
}
