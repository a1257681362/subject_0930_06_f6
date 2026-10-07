package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.BladeRequest;
import com.evops.dto.ReplacementRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.dto.TurbineUpdateRequest;
import com.evops.entity.Blade;
import com.evops.entity.BladeReplacement;
import com.evops.entity.Turbine;
import com.evops.service.BladeService;
import com.evops.service.TurbineService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/turbines")
public class TurbineController {

    private final TurbineService turbineService;
    private final BladeService bladeService;

    public TurbineController(TurbineService turbineService, BladeService bladeService) {
        this.turbineService = turbineService;
        this.bladeService = bladeService;
    }

    /** 建档：创建风机（可同时录入首批叶片） */
    @PostMapping
    public ApiResponse<Turbine> create(@Validated @RequestBody TurbineCreateRequest req) {
        return ApiResponse.ok(turbineService.create(req));
    }

    /** 档案调整：如把并网时间调到临界时间 */
    @PutMapping("/{id}")
    public ApiResponse<Turbine> update(@PathVariable Long id,
                                       @RequestBody TurbineUpdateRequest req) {
        return ApiResponse.ok(turbineService.updateArchive(id, req));
    }

    @GetMapping("/{id}")
    public ApiResponse<Turbine> detail(@PathVariable Long id) {
        return ApiResponse.ok(turbineService.mustGet(id));
    }

    @GetMapping
    public ApiResponse<List<Turbine>> list() {
        return ApiResponse.ok(turbineService.list());
    }

    /**
     * 将风电叶片运维对象置为无效（只置无效不删除）。
     * 置无效后再查"叶片结冰风险预警"，页面、导出与后台按同一口径排除该风机。
     */
    @PostMapping("/{id}/deactivate")
    public ApiResponse<Turbine> deactivate(@PathVariable Long id,
                                           @RequestParam(required = false, defaultValue = "系统管理员") String operator) {
        return ApiResponse.ok(turbineService.deactivate(id, operator));
    }

    /** 补录叶片 */
    @PostMapping("/{id}/blades")
    public ApiResponse<Blade> addBlade(@PathVariable Long id,
                                       @Validated @RequestBody BladeRequest req) {
        return ApiResponse.ok(bladeService.addBlade(id, req));
    }

    /** 换件：保留旧件与新件的前后关系 */
    @PostMapping("/{id}/replacements")
    public ApiResponse<BladeReplacement> replace(@PathVariable Long id,
                                                 @Validated @RequestBody ReplacementRequest req) {
        return ApiResponse.ok(bladeService.replace(id, req));
    }

    /** 当前在役叶片 */
    @GetMapping("/{id}/blades")
    public ApiResponse<List<Blade>> blades(@PathVariable Long id) {
        return ApiResponse.ok(bladeService.listInServiceAt(id, LocalDateTime.now()));
    }

    /**
     * 归属口径验证：查询某时刻在役的叶片。
     * 口径与风机服役窗口一致：[装上时刻, 拆下时刻)，左闭右开。
     */
    @GetMapping("/{id}/blades/at")
    public ApiResponse<List<Blade>> bladesAt(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        return ApiResponse.ok(bladeService.listInServiceAt(id, time));
    }

    /** 叶片全量档案 + 换件前后关系 */
    @GetMapping("/{id}/blades/history")
    public ApiResponse<Map<String, Object>> history(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("blades", bladeService.listAll(id));
        result.put("replacements", bladeService.listReplacements(id));
        return ApiResponse.ok(result);
    }
}
