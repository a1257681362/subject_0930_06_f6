package com.evops.controller;

import com.evops.dto.IcingAlertFilter;
import com.evops.dto.IcingAlertRow;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingRiskLevel;
import com.evops.service.IcingAlertService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 叶片结冰风险预警页面与导出。
 * 页面列表与 CSV 导出、后台接口共用 IcingAlertService.listAlerts 同一查询口径
 * （联表风机并排除 run_status=INVALID 的无效运维对象）。
 */
@Controller
public class IcingPageController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IcingAlertService alertService;

    public IcingPageController(IcingAlertService alertService) {
        this.alertService = alertService;
    }

    /** 叶片结冰风险预警页面 */
    @GetMapping("/icing-alerts")
    public String page(@RequestParam(required = false) Long turbineId,
                       @RequestParam(required = false) IcingAlertStatus status,
                       @RequestParam(required = false) IcingRiskLevel riskLevel,
                       Model model) {
        IcingAlertFilter filter = new IcingAlertFilter();
        filter.setTurbineId(turbineId);
        filter.setStatus(status);
        filter.setRiskLevel(riskLevel);
        List<IcingAlertRow> rows = alertService.listAlerts(filter, false);
        model.addAttribute("rows", rows);
        model.addAttribute("statuses", IcingAlertStatus.values());
        model.addAttribute("levels", IcingRiskLevel.values());
        model.addAttribute("qTurbineId", turbineId);
        model.addAttribute("qStatus", status);
        model.addAttribute("qRiskLevel", riskLevel);
        return "icing-alerts";
    }

    /** 导出 CSV：与页面、后台同口径 */
    @GetMapping("/icing-alerts/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long turbineId,
                                         @RequestParam(required = false) IcingAlertStatus status,
                                         @RequestParam(required = false) IcingRiskLevel riskLevel) {
        IcingAlertFilter filter = new IcingAlertFilter();
        filter.setTurbineId(turbineId);
        filter.setStatus(status);
        filter.setRiskLevel(riskLevel);
        List<IcingAlertRow> rows = alertService.listAlerts(filter, false);

        StringBuilder sb = new StringBuilder();
        sb.append("告警编号,风机编号,场址,监测时刻,温度(℃),湿度(%),风速(m/s),振动(g),风险得分,风险等级,停机建议,状态,停机确认人,停机确认时刻,解除确认人,解除确认时刻,模型版本\r\n");
        for (IcingAlertRow r : rows) {
            sb.append(csv(r.getAlertNo())).append(',')
                    .append(csv(r.getTurbineNo())).append(',')
                    .append(csv(r.getSite())).append(',')
                    .append(ts(r)).append(',')
                    .append(r.getTemperature()).append(',')
                    .append(r.getHumidityPct()).append(',')
                    .append(r.getWindSpeed()).append(',')
                    .append(r.getVibration()).append(',')
                    .append(r.getScore()).append(',')
                    .append(csv(r.getRiskLevel() == null ? "" : r.getRiskLevel().name())).append(',')
                    .append(csv(r.getShutdownAdvice())).append(',')
                    .append(csv(r.getStatus() == null ? "" : r.getStatus().name())).append(',')
                    .append(csv(r.getShutdownConfirmedBy())).append(',')
                    .append(fmt(r.getShutdownConfirmedAt())).append(',')
                    .append(csv(r.getClearConfirmedBy())).append(',')
                    .append(fmt(r.getClearConfirmedAt())).append(',')
                    .append('v').append(r.getModelVersionNo()).append("\r\n");
        }
        // UTF-8 BOM，保证 Excel 打开中文不乱码
        byte[] body = ("﻿" + sb).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=icing-alerts.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private String ts(IcingAlertRow r) {
        return r.getMonitorTime() == null ? "" : FMT.format(r.getMonitorTime());
    }

    private String fmt(java.time.LocalDateTime t) {
        return t == null ? "" : FMT.format(t);
    }

    /** CSV 字段转义：含逗号/引号/换行时加双引号，内部引号翻倍 */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
