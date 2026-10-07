package com.evops.enums;

/** 高空作业任务状态机：排班中→已排班→作业中→已完成；未开工可取消 */
public enum MaintenanceTaskStatus {
    PLANNING, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
}
