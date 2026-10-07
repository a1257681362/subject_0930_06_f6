package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 巡检结果入参：登记无人机影像与巡检时刻 */
@Data
public class InspectSubmitRequest {
    @NotBlank(message = "无人机影像地址不能为空")
    private String droneImageUrl;
    @NotNull(message = "巡检时刻不能为空")
    private LocalDateTime inspectTime;
}
