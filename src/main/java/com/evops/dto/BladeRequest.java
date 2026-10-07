package com.evops.dto;

import com.evops.enums.BladePosition;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 叶片建档/换件入参 */
@Data
public class BladeRequest {
    @NotBlank(message = "叶片编号不能为空")
    private String bladeNo;
    @NotBlank(message = "序列号不能为空")
    private String serialNo;
    @NotNull(message = "方位不能为空")
    private BladePosition bladePosition;
    @NotBlank(message = "材料不能为空")
    private String material;
    /** 装上时刻；为空时默认取所属风机的并网时间（与风机档案同一口径） */
    private LocalDateTime installedAt;
}
