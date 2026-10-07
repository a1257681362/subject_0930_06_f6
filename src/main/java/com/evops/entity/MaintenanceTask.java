package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.MaintenanceTaskStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 高空作业任务：关联缺陷，声明资质要求、人数、装备类型与维修时长。
 * 每个任务至多一个确认班次（唯一可用排班结果）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_maintenance_task")
public class MaintenanceTask extends BaseEntity {
    /** 任务编号，全局唯一 */
    private String taskNo;
    private Long turbineId;
    /** 来源缺陷，可空 */
    private Long defectId;
    /** 要求的资质类型，如 HIGH_ALTITUDE */
    private String requiredQualification;
    /** 所需人数 */
    private Integer requiredWorkers;
    /** 所需装备类型，可空表示不要求装备 */
    private String requiredEquipmentType;
    /** 维修时长（小时），候选班次长度 */
    private Integer estimatedDurationHours;
    private MaintenanceTaskStatus status;
}
