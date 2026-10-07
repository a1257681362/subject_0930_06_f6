package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 结冰告警人工确认入参（停机确认 / 解除风险确认共用） */
@Data
public class IcingConfirmRequest {
    @NotBlank(message = "确认人不能为空")
    private String confirmer;
    /** 确认意见 / 驳回原因（解除驳回时必填原因由业务校验） */
    private String opinion;
}
