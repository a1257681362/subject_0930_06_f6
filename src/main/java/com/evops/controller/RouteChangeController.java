package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.ApprovalRequest;
import com.evops.dto.RouteChangeCreateRequest;
import com.evops.entity.RouteChangeRequest;
import com.evops.service.RouteChangeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/route-changes")
public class RouteChangeController {

    private final RouteChangeService routeChangeService;

    public RouteChangeController(RouteChangeService routeChangeService) {
        this.routeChangeService = routeChangeService;
    }

    /** 发起临时改线申请（留存与基准版本的差异） */
    @PostMapping
    public ApiResponse<RouteChangeRequest> create(@Validated @RequestBody RouteChangeCreateRequest req) {
        return ApiResponse.ok(routeChangeService.create(req));
    }

    /** 详情：含拟改航点、版本差异、审批记录 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(routeChangeService.detail(id));
    }

    /** 航线改线申请列表 */
    @GetMapping
    public ApiResponse<List<RouteChangeRequest>> listByRoute(@RequestParam Long routeId) {
        return ApiResponse.ok(routeChangeService.listByRoute(routeId));
    }

    /** 审批当前级次（1 场站 → 2 运检中心）；只批一级时申请停留待处理 */
    @PostMapping("/{id}/approve")
    public ApiResponse<RouteChangeRequest> approve(@PathVariable Long id,
                                                   @Validated @RequestBody ApprovalRequest req) {
        return ApiResponse.ok(routeChangeService.approve(id, req));
    }
}
