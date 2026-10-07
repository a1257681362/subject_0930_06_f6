package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 维修结果入参 */
@Data
public class RepairRequest {
    @NotBlank(message = "维修结果不能为空")
    private String repairResult;
}
