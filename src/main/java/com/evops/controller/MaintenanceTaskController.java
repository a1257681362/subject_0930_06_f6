package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.MaintenanceTaskCreateRequest;
import com.evops.dto.TaskDetailDto;
import com.evops.entity.MaintenanceTask;
import com.evops.entity.WorkShift;
import com.evops.service.MaintenanceTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance-tasks")
public class MaintenanceTaskController {

    private final MaintenanceTaskService maintenanceTaskService;

    public MaintenanceTaskController(MaintenanceTaskService maintenanceTaskService) {
        this.maintenanceTaskService = maintenanceTaskService;
    }

    /** 创建高空作业任务（资质要求 + 人数 + 装备类型 + 维修时长） */
    @PostMapping
    public ApiResponse<MaintenanceTask> create(@Validated @RequestBody MaintenanceTaskCreateRequest req) {
        return ApiResponse.ok(maintenanceTaskService.create(req));
    }

    /** 详情（含全部班次） */
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceTaskService.detail(id));
    }

    /** 生成候选班次（幂等：重试只留一套候选；已确认班次的任务拒绝重新生成） */
    @PostMapping("/{id}/generate-shifts")
    public ApiResponse<List<WorkShift>> generateShifts(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceTaskService.generateShifts(id));
    }
}
