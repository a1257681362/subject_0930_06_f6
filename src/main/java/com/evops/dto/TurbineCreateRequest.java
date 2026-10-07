package com.evops.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** 风机建档入参，可同时录入首批叶片 */
@Data
public class TurbineCreateRequest {
    @NotBlank(message = "风机编号不能为空")
    private String turbineNo;
    @NotBlank(message = "场址不能为空")
    private String site;
    @NotBlank(message = "机型不能为空")
    private String model;
    @NotNull(message = "并网时间不能为空")
    private LocalDateTime gridConnectedAt;
    @Valid
    private List<BladeRequest> blades;
}
