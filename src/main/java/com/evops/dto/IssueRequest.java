package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 维修领用入参：绑定风机与缺陷单 */
@Data
public class IssueRequest {
    /** 领用单号，缺省自动生成 */
    private String reqNo;
    @NotNull(message = "风机不能为空")
    private Long turbineId;
    @NotNull(message = "缺陷单不能为空")
    private Long defectId;
    @NotNull(message = "备件不能为空")
    private Long partId;
    @NotBlank(message = "领用人不能为空")
    private String issuedBy;
    private String purpose;
}
