package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 改线审批入参。审批按当前级次推进（1 场站 → 2 运检中心）；
 * 终审时可填报当前风速/电量用于发布前复核，不填则只核禁飞区。
 */
@Data
public class ApprovalRequest {
    @NotBlank(message = "审批人不能为空")
    private String approver;
    @NotNull(message = "审批结论不能为空")
    private Boolean pass;
    /** 审批意见 */
    private String opinion;
    /** 终审复核用：当前风速（m/s），可选 */
    private Double windSpeed;
    /** 终审复核用：当前电量（%），可选 */
    private Integer batteryPct;
}
