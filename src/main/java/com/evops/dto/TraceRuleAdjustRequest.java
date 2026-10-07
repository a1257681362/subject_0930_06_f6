package com.evops.dto;

import com.evops.enums.TraceLevel;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 序列追踪规则调整入参：只生成新版本，不改动旧版本 */
@Data
public class TraceRuleAdjustRequest {
    @NotNull(message = "追踪级别不能为空")
    private TraceLevel traceLevel;
    @NotNull(message = "入库是否核验不能为空")
    private Boolean needInboundCheck;
    /** 库存期上限（月），空表示不限 */
    private Integer maxStockMonths;
    @NotBlank(message = "规则内容不能为空")
    private String ruleContent;
    @NotBlank(message = "操作人不能为空")
    private String operator;
}
