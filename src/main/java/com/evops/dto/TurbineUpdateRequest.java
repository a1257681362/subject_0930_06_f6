package com.evops.dto;

import com.evops.enums.TurbineStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 风机档案调整入参（如把并网时间调到临界时间），字段均为可选 */
@Data
public class TurbineUpdateRequest {
    private String site;
    private String model;
    private LocalDateTime gridConnectedAt;
    private LocalDateTime decommissionedAt;
    private TurbineStatus status;
}
