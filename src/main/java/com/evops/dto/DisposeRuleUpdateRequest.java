package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 处置时限配置调整入参 */
@Data
public class DisposeRuleUpdateRequest {
    @NotNull(message = "处置时限不能为空")
    @Min(value = 1, message = "处置时限至少 1 小时")
    private Integer disposeHours;
    @NotBlank(message = "维修建议模板不能为空")
    private String adviceTemplate;
}
