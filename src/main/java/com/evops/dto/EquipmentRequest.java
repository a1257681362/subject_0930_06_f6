package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 装备登记入参 */
@Data
public class EquipmentRequest {
    @NotNull(message = "装备编号不能为空")
    private String equipmentNo;
    @NotNull(message = "装备名称不能为空")
    private String name;
    @NotNull(message = "装备类型不能为空")
    private String equipmentType;
    /** 可用窗口起点，空表示不限 */
    private LocalDateTime availableFrom;
    /** 可用窗口终点，空表示不限 */
    private LocalDateTime availableTo;
}
