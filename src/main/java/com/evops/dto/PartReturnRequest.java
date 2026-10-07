package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 退库申请入参 */
@Data
public class PartReturnRequest {
    /** 退库单号，缺省自动生成 */
    private String returnNo;
    @NotBlank(message = "退库人不能为空")
    private String returnedBy;
}
