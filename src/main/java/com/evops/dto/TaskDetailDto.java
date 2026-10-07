package com.evops.dto;

import com.evops.entity.MaintenanceTask;
import com.evops.entity.WorkShift;
import lombok.Data;

import java.util.List;

/** 任务详情：任务 + 全部班次（候选/确认/历史） */
@Data
public class TaskDetailDto {
    private MaintenanceTask task;
    private List<WorkShift> shifts;
}
