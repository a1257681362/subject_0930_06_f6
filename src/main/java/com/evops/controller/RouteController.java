package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.CheckResultDto;
import com.evops.dto.PublishCheckRequest;
import com.evops.dto.RouteCreateRequest;
import com.evops.dto.RouteVersionRequest;
import com.evops.entity.FlightRoute;
import com.evops.entity.FlightRouteVersion;
import com.evops.service.RouteService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /** 新建航线（含首版本草稿） */
    @PostMapping
    public ApiResponse<FlightRoute> create(@Validated @RequestBody RouteCreateRequest req) {
        return ApiResponse.ok(routeService.create(req));
    }

    /** 航线详情（含全部版本，历史版本可追溯到航点级） */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("route", routeService.mustGet(id));
        result.put("versions", routeService.listVersions(id));
        return ApiResponse.ok(result);
    }

    /** 风机航线列表 */
    @GetMapping("/by-turbine/{turbineId}")
    public ApiResponse<List<FlightRoute>> byTurbine(@PathVariable Long turbineId) {
        return ApiResponse.ok(routeService.listByTurbine(turbineId));
    }

    /** 新增版本草稿 */
    @PostMapping("/{id}/versions")
    public ApiResponse<FlightRouteVersion> addVersion(@PathVariable Long id,
                                                      @Validated @RequestBody RouteVersionRequest req) {
        return ApiResponse.ok(routeService.addVersion(id, req));
    }

    /** 版本详情（含航点串） */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<FlightRouteVersion> versionDetail(@PathVariable Long versionId) {
        return ApiResponse.ok(routeService.versionDetail(versionId));
    }

    /** 发布前校验（试算）：返回冲突明细，不推进状态 */
    @PostMapping("/versions/{versionId}/check")
    public ApiResponse<CheckResultDto> check(@PathVariable Long versionId,
                                             @Validated @RequestBody PublishCheckRequest req) {
        return ApiResponse.ok(routeService.check(versionId, req));
    }

    /** 发布：校验通过才生效；新版本只作用于未起飞任务 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<FlightRouteVersion> publish(@PathVariable Long versionId,
                                                   @Validated @RequestBody PublishCheckRequest req) {
        return ApiResponse.ok(routeService.publish(versionId, req));
    }
}
