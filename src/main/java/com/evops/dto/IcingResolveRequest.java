package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 解除风险确认：停机后须再次人工确认，告警才闭环 */
@Data
public class IcingResolveRequest {
    @NotBlank(message = "确认人不能为空")
    private String resolvedBy;
    private String remark;
    private java.time.LocalDateTime resolvedAt;
}
