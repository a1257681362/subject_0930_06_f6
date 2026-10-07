package com.evops.dto;

import com.evops.enums.DefectLevel;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 专家复核入参：可升级或降级，不覆盖初判；等级变化记录责任人 */
@Data
public class ReviewRequest {
    @NotBlank(message = "复核人不能为空")
    private String reviewer;
    @NotNull(message = "复核等级不能为空")
    private DefectLevel toLevel;
    private String opinion;
    @NotBlank(message = "责任人不能为空")
    private String responsiblePerson;
}
