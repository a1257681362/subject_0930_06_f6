package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.IcingAlertDetail;
import com.evops.dto.IcingAlertFilter;
import com.evops.dto.IcingAlertRow;
import com.evops.dto.IcingConfirmRequest;
import com.evops.dto.IcingEvaluateResult;
import com.evops.dto.IcingModelAdjustRequest;
import com.evops.dto.IcingMonitorRequest;
import com.evops.entity.IcingAlert;
import com.evops.entity.IcingModelVersion;
import com.evops.entity.IcingMonitorData;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingRiskLevel;
import com.evops.service.IcingAlertService;
import com.evops.service.IcingModelService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 叶片结冰风险预警后台接口。
 * 列表与页面、导出共用 IcingAlertService.listAlerts 同一查询口径（排除已置无效风机）。
 */
@RestController
@RequestMapping("/api/icing")
public class IcingController {

    private final IcingAlertService alertService;
    private final IcingModelService modelService;

    public IcingController(IcingAlertService alertService, IcingModelService modelService) {
        this.alertService = alertService;
        this.modelService = modelService;
    }

    /** 上报监测数据（温度/湿度/风速/历史振动）并评估；读数缺失或异常返回待补标志 */
    @PostMapping("/monitor-data")
    public ApiResponse<IcingEvaluateResult> report(@Validated @RequestBody IcingMonitorRequest req) {
        return ApiResponse.ok(alertService.report(req));
    }

    /** 监测数据查询（?pending=true 只看待补） */
    @GetMapping("/monitor-data")
    public ApiResponse<List<IcingMonitorData>> monitorData(
            @RequestParam(required = false) Long turbineId,
            @RequestParam(required = false, defaultValue = "false") boolean pending) {
        return ApiResponse.ok(alertService.listMonitorData(turbineId, pending));
    }

    /**
     * 结冰风险预警列表（后台口径）。
     * 可按 turbineId/status/riskLevel 过滤；onlyOpen=true 只看未闭环（预警中/已停机待解除）。
     */
    @GetMapping("/alerts")
    public ApiResponse<List<IcingAlertRow>> alerts(
            @RequestParam(required = false) Long turbineId,
            @RequestParam(required = false) IcingAlertStatus status,
            @RequestParam(required = false) IcingRiskLevel riskLevel,
            @RequestParam(required = false, defaultValue = "false") boolean onlyOpen) {
        IcingAlertFilter filter = new IcingAlertFilter();
        filter.setTurbineId(turbineId);
        filter.setStatus(status);
        filter.setRiskLevel(riskLevel);
        return ApiResponse.ok(alertService.listAlerts(filter, onlyOpen));
    }

    /** 告警详情：固化的读数/得分/等级/模型版本 + 两次确认记录 + 通知回执 */
    @GetMapping("/alerts/{id}")
    public ApiResponse<IcingAlertDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(alertService.getAlertDetail(id));
    }

    /** 第一次人工确认：确认后才执行停机 */
    @PostMapping("/alerts/{id}/confirm-shutdown")
    public ApiResponse<IcingAlert> confirmShutdown(@PathVariable Long id,
                                                   @Validated @RequestBody IcingConfirmRequest req) {
        return ApiResponse.ok(alertService.confirmShutdown(id, req));
    }

    /** 第二次人工确认：pass=true 解除风险闭环；pass=false 驳回，可再次确认 */
    @PostMapping("/alerts/{id}/confirm-clear")
    public ApiResponse<IcingAlert> confirmClear(@PathVariable Long id,
                                                @RequestParam(defaultValue = "true") boolean pass,
                                                @Validated @RequestBody IcingConfirmRequest req) {
        return ApiResponse.ok(alertService.confirmClear(id, pass, req));
    }

    /** 模型参数全量版本（含已退役），旧版本内容可查 */
    @GetMapping("/model/versions")
    public ApiResponse<List<IcingModelVersion>> modelVersions() {
        return ApiResponse.ok(modelService.listVersions());
    }

    /** 模型参数调整：只生成新版本，旧版本退役，历史告警不重算 */
    @PostMapping("/model/versions")
    public ApiResponse<IcingModelVersion> adjustModel(@Validated @RequestBody IcingModelAdjustRequest req) {
        return ApiResponse.ok(modelService.adjust(req));
    }
}
