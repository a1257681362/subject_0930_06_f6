package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.FlightTaskCreateRequest;
import com.evops.entity.FlightTask;
import com.evops.service.FlightTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flight-tasks")
public class FlightTaskController {

    private final FlightTaskService flightTaskService;

    public FlightTaskController(FlightTaskService flightTaskService) {
        this.flightTaskService = flightTaskService;
    }

    /** 创建飞行任务（绑定航线当前发布版本） */
    @PostMapping
    public ApiResponse<FlightTask> create(@Validated @RequestBody FlightTaskCreateRequest req) {
        return ApiResponse.ok(flightTaskService.create(req));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ApiResponse<FlightTask> detail(@PathVariable Long id) {
        return ApiResponse.ok(flightTaskService.mustGet(id));
    }

    /** 起飞：版本自此锁定 */
    @PostMapping("/{id}/takeoff")
    public ApiResponse<FlightTask> takeoff(@PathVariable Long id) {
        return ApiResponse.ok(flightTaskService.takeoff(id));
    }

    /** 完成 */
    @PostMapping("/{id}/complete")
    public ApiResponse<FlightTask> complete(@PathVariable Long id) {
        return ApiResponse.ok(flightTaskService.complete(id));
    }

    /** 取消（仅未起飞） */
    @PostMapping("/{id}/cancel")
    public ApiResponse<FlightTask> cancel(@PathVariable Long id) {
        return ApiResponse.ok(flightTaskService.cancel(id));
    }
}
