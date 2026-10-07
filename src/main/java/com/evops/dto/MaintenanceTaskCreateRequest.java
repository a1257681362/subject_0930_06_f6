package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 高空作业任务创建入参 */
@Data
public class MaintenanceTaskCreateRequest {
    /** 任务编号；为空时自动生成 */
    private String taskNo;
    @NotNull(message = "风机不能为空")
    private Long turbineId;
    /** 来源缺陷，可空 */
    private Long defectId;
    @NotNull(message = "资质要求不能为空")
    private String requiredQualification;
    /** 所需人数，默认 2 */
    private Integer requiredWorkers;
    /** 所需装备类型，可空表示不要求装备 */
    private String requiredEquipmentType;
    @NotNull(message = "维修时长不能为空")
    private Integer estimatedDurationHours;
}
