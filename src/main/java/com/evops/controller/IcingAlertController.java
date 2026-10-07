package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.IcingModelParamRequest;
import com.evops.dto.IcingObservationRequest;
import com.evops.dto.IcingResolveRequest;
import com.evops.dto.IcingShutdownConfirmRequest;
import com.evops.dto.IcingSupplementRequest;
import com.evops.dto.IcingWarningRow;
import com.evops.entity.IcingAlert;
import com.evops.entity.IcingModelParam;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingObservationStatus;
import com.evops.service.IcingAlertService;
import com.evops.service.IcingModelParamService;
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

/**
 * 叶片结冰风险预警接口。
 * 列表（后台）、页面与导出共用 IcingAlertService.listWarningRows，同口径仅统计有效叶片。
 */
@RestController
@RequestMapping("/api/icing")
public class IcingAlertController {

    private final IcingAlertService alertService;
    private final IcingModelParamService paramService;

    public IcingAlertController(IcingAlertService alertService, IcingModelParamService paramService) {
        this.alertService = alertService;
        this.paramService = paramService;
    }

    // ---------------- 观测 ----------------

    /** 上报观测：温度/湿度/风速/历史振动；缺失或异常自动标记待补 */
    @PostMapping("/observations")
    public ApiResponse<com.evops.entity.IcingObservation> report(
            @Validated @RequestBody IcingObservationRequest req) {
        return ApiResponse.ok(alertService.report(req));
    }

    /** 待补观测补传：补齐后按首次上报时的同一参数版本重算 */
    @PostMapping("/observations/{id}/supplement")
    public ApiResponse<com.evops.entity.IcingObservation> supplement(
            @PathVariable Long id, @Validated @RequestBody IcingSupplementRequest req) {
        return ApiResponse.ok(alertService.supplement(id, req));
    }

    /** 观测列表（?bladeId=&status=OK/PENDING_SUPPLEMENT） */
    @GetMapping("/observations")
    public ApiResponse<List<com.evops.entity.IcingObservation>> observations(
            @RequestParam(required = false) Long bladeId,
            @RequestParam(required = false) IcingObservationStatus status) {
        return ApiResponse.ok(alertService.listObservations(bladeId, status));
    }

    // ---------------- 两次人工确认 ----------------

    /** 停机人工确认：达到阈值只生成建议，确认后才执行停机 */
    @PostMapping("/alerts/{id}/confirm-shutdown")
    public ApiResponse<IcingAlert> confirmShutdown(
            @PathVariable Long id, @Validated @RequestBody IcingShutdownConfirmRequest req) {
        return ApiResponse.ok(alertService.confirmShutdown(id, req));
    }

    /** 解除风险：停机后再次人工确认，告警闭环 */
    @PostMapping("/alerts/{id}/resolve")
    public ApiResponse<IcingAlert> confirmResolve(
            @PathVariable Long id, @Validated @RequestBody IcingResolveRequest req) {
        return ApiResponse.ok(alertService.confirmResolve(id, req));
    }

    /** 预警详情（观测 + 通知回执） */
    @GetMapping("/alerts/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(alertService.alertDetail(id));
    }

    /**
     * 叶片结冰风险预警列表（后台口径）：只统计有效叶片。
     * 与 GET /icing/alerts/page、GET /icing/alerts/export 同源。
     */
    @GetMapping("/alerts")
    public ApiResponse<List<IcingWarningRow>> list(
            @RequestParam(required = false) IcingAlertStatus status,
            @RequestParam(required = false) Long turbineId) {
        return ApiResponse.ok(alertService.listWarningRows(status, turbineId));
    }

    // ---------------- 模型参数版本 ----------------

    /** 参数全量版本（含已退役） */
    @GetMapping("/model-params")
    public ApiResponse<List<IcingModelParam>> listParams() {
        return ApiResponse.ok(paramService.listVersions());
    }

    /** 参数调整：只生成新版本，旧版本退役，历史告警不重算 */
    @PostMapping("/model-params/versions")
    public ApiResponse<IcingModelParam> adjustParam(@Validated @RequestBody IcingModelParamRequest req) {
        return ApiResponse.ok(paramService.adjust(req));
    }
}
