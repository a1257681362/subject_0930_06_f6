package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 验收入参：通过或驳回 */
@Data
public class AcceptRequest {
    @NotNull(message = "验收结论不能为空")
    private Boolean pass;
    private String remark;
}
