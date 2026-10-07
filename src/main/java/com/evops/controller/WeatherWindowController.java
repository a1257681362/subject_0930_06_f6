package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.WeatherWindowRequest;
import com.evops.entity.WeatherWindow;
import com.evops.service.WeatherWindowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weather-windows")
public class WeatherWindowController {

    private final WeatherWindowService weatherWindowService;

    public WeatherWindowController(WeatherWindowService weatherWindowService) {
        this.weatherWindowService = weatherWindowService;
    }

    /** 上报天气窗口（补传/补录/重试按 风机+起止 归并，只形成一条可用结果） */
    @PostMapping
    public ApiResponse<WeatherWindow> report(@Validated @RequestBody WeatherWindowRequest req) {
        return ApiResponse.ok(weatherWindowService.report(req));
    }

    /** 窗口列表（可按风机过滤） */
    @GetMapping
    public ApiResponse<List<WeatherWindow>> list(@RequestParam(required = false) Long turbineId) {
        return ApiResponse.ok(weatherWindowService.listByTurbine(turbineId));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ApiResponse<WeatherWindow> detail(@PathVariable Long id) {
        return ApiResponse.ok(weatherWindowService.mustGet(id));
    }
}
