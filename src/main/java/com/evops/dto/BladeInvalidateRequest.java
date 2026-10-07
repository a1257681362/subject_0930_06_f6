package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 风电叶片置为无效（报废/拆除停用）：无效后不再参与结冰预警，页面/导出/后台同口径 */
@Data
public class BladeInvalidateRequest {
    @NotBlank(message = "原因不能为空")
    private String reason;
    /** 操作人 */
    private String operator;
}
