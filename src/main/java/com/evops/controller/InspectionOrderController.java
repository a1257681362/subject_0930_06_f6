package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.OrderCreateRequest;
import com.evops.entity.Defect;
import com.evops.entity.InspectionOrder;
import com.evops.service.InspectionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inspection-orders")
public class InspectionOrderController {

    private final InspectionService inspectionService;

    public InspectionOrderController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /** 创建巡检工单 */
    @PostMapping
    public ApiResponse<InspectionOrder> create(@Validated @RequestBody OrderCreateRequest req) {
        return ApiResponse.ok(inspectionService.createOrder(req));
    }

    /** 提交巡检结果：无人机影像 + 巡检时刻 */
    @PostMapping("/{id}/inspect")
    public ApiResponse<InspectionOrder> inspect(@PathVariable Long id,
                                                @Validated @RequestBody InspectSubmitRequest req) {
        return ApiResponse.ok(inspectionService.submitInspection(id, req));
    }

    /** 缺陷登记 */
    @PostMapping("/{id}/defects")
    public ApiResponse<Defect> registerDefect(@PathVariable Long id,
                                              @Validated @RequestBody DefectRegisterRequest req) {
        return ApiResponse.ok(inspectionService.registerDefect(id, req));
    }

    /** 无缺陷闭环 */
    @PostMapping("/{id}/close")
    public ApiResponse<InspectionOrder> close(@PathVariable Long id) {
        return ApiResponse.ok(inspectionService.close(id));
    }

    /** 工单详情（含缺陷列表） */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("order", inspectionService.mustGetOrder(id));
        result.put("defects", inspectionService.listDefects(id));
        return ApiResponse.ok(result);
    }
}
