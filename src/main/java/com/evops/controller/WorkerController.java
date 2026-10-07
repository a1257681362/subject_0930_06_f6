package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.WorkerRequest;
import com.evops.entity.Worker;
import com.evops.service.WorkerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    /** 人员登记（资质类型 + 有效期） */
    @PostMapping
    public ApiResponse<Worker> register(@Validated @RequestBody WorkerRequest req) {
        return ApiResponse.ok(workerService.register(req));
    }

    /** 人员列表 */
    @GetMapping
    public ApiResponse<List<Worker>> list() {
        return ApiResponse.ok(workerService.listAll());
    }
}
