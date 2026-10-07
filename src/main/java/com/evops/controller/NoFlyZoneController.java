package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.NoFlyZoneRequest;
import com.evops.entity.NoFlyZone;
import com.evops.service.NoFlyZoneService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/no-fly-zones")
public class NoFlyZoneController {

    private final NoFlyZoneService noFlyZoneService;

    public NoFlyZoneController(NoFlyZoneService noFlyZoneService) {
        this.noFlyZoneService = noFlyZoneService;
    }

    /** 划设禁飞区（临时/永久） */
    @PostMapping
    public ApiResponse<NoFlyZone> create(@Validated @RequestBody NoFlyZoneRequest req) {
        return ApiResponse.ok(noFlyZoneService.create(req));
    }

    /** 列表：默认全量（含已取消/已过期，可追溯）；activeOnly=true 只看当前生效 */
    @GetMapping
    public ApiResponse<List<NoFlyZone>> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(activeOnly
                ? noFlyZoneService.listActiveAt(LocalDateTime.now())
                : noFlyZoneService.listAll());
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ApiResponse<NoFlyZone> detail(@PathVariable Long id) {
        return ApiResponse.ok(noFlyZoneService.mustGet(id));
    }

    /** 取消（不删除，保留追溯） */
    @PostMapping("/{id}/cancel")
    public ApiResponse<NoFlyZone> cancel(@PathVariable Long id) {
        return ApiResponse.ok(noFlyZoneService.cancel(id));
    }
}
