package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.ConfirmWeatherRequest;
import com.evops.dto.PostponeRequest;
import com.evops.dto.ShiftDetailDto;
import com.evops.entity.WeatherConfirmation;
import com.evops.entity.WorkShift;
import com.evops.service.WorkShiftService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shifts")
public class WorkShiftController {

    private final WorkShiftService workShiftService;

    public WorkShiftController(WorkShiftService workShiftService) {
        this.workShiftService = workShiftService;
    }

    /** 详情（人员装备分配 + 二次确认 + 延期记录 + 通知回执） */
    @GetMapping("/{id}")
    public ApiResponse<ShiftDetailDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(workShiftService.detail(id));
    }

    /** 确认班次（任务唯一可用结果；过期资质拦截；幂等重试） */
    @PostMapping("/{id}/confirm")
    public ApiResponse<WorkShift> confirm(@PathVariable Long id) {
        return ApiResponse.ok(workShiftService.confirm(id));
    }

    /** 作业前二次确认天气 */
    @PostMapping("/{id}/confirm-weather")
    public ApiResponse<WeatherConfirmation> confirmWeather(@PathVariable Long id,
                                                           @Validated @RequestBody ConfirmWeatherRequest req) {
        return ApiResponse.ok(workShiftService.confirmWeather(id, req));
    }

    /** 开工（须最近一次二次确认为 PASS） */
    @PostMapping("/{id}/start")
    public ApiResponse<WorkShift> start(@PathVariable Long id) {
        return ApiResponse.ok(workShiftService.start(id));
    }

    /** 完工 */
    @PostMapping("/{id}/complete")
    public ApiResponse<WorkShift> complete(@PathVariable Long id) {
        return ApiResponse.ok(workShiftService.complete(id));
    }

    /** 延期（保留原计划与通知回执；显式改期，非静默） */
    @PostMapping("/{id}/postpone")
    public ApiResponse<WorkShift> postpone(@PathVariable Long id,
                                           @Validated @RequestBody PostponeRequest req) {
        return ApiResponse.ok(workShiftService.postpone(id, req));
    }

    /** 取消（候选/已确认；确认取消后任务回到排班中） */
    @PostMapping("/{id}/cancel")
    public ApiResponse<WorkShift> cancel(@PathVariable Long id) {
        return ApiResponse.ok(workShiftService.cancel(id));
    }
}
