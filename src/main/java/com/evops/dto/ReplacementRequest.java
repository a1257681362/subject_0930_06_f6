package com.evops.dto;

import com.evops.enums.BladePosition;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 换件入参：换下某方位旧叶片，装上新叶片，保留前后关系 */
@Data
public class ReplacementRequest {
    @NotNull(message = "方位不能为空")
    private BladePosition bladePosition;
    @NotNull(message = "新叶片信息不能为空")
    @Valid
    private BladeRequest newBlade;
    @NotNull(message = "换件时刻不能为空")
    private LocalDateTime replacedAt;
    private String reason;
}
