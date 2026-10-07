package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.EquipmentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 装备：可用状态与可用窗口 [availableFrom, availableTo)，空表示不限。
 * 维保中或可用窗口不覆盖班次的装备不可排班。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_equipment")
public class Equipment extends BaseEntity {
    /** 装备编号，全局唯一 */
    private String equipmentNo;
    private String name;
    /** 装备类型，如 升降平台 */
    private String equipmentType;
    private EquipmentStatus status;
    private LocalDateTime availableFrom;
    private LocalDateTime availableTo;
}
