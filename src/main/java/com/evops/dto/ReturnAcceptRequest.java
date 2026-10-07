package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 退库验收入参：pass=true 验收通过回库 / false 驳回退回已领用 */
@Data
public class ReturnAcceptRequest {
    @NotNull(message = "验收结论不能为空")
    private Boolean pass;
    @NotBlank(message = "验收人不能为空")
    private String acceptedBy;
    private String remark;
}
