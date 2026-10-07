package com.evops.dto;

import com.evops.enums.DefectLevel;
import com.evops.enums.DefectType;
import lombok.Data;

import javax.validation.constraints.NotNull;

/** 缺陷登记入参：defectLevel 作为初判等级，复核不覆盖 */
@Data
public class DefectRegisterRequest {
    @NotNull(message = "叶片不能为空")
    private Long bladeId;
    @NotNull(message = "缺陷分类不能为空")
    private DefectType defectType;
    @NotNull(message = "缺陷等级不能为空")
    private DefectLevel defectLevel;
    private String description;
}
