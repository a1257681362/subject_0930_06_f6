package com.evops.controller;

import com.evops.dto.IcingWarningRow;
import com.evops.entity.IcingAlert;
import com.evops.enums.IcingAlertStatus;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 叶片结冰风险预警页面与导出。
 * 页面（Thymeleaf）、导出（CSV）与后台接口共用 {@link IcingAlertService#listWarningRows}，
 * 保证三处同口径：仅统计有效叶片。
 */
@Controller
public class IcingViewController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IcingAlertService alertService;

    public IcingViewController(IcingAlertService alertService) {
        this.alertService = alertService;
    }

    /** 预警页面 */
    @GetMapping("/icing/alerts/page")
    public String page(@RequestParam(required = false) IcingAlertStatus status,
                       @RequestParam(required = false) Long turbineId,
                       Model model) {
        List<IcingWarningRow> rows = alertService.listWarningRows(status, turbineId);
        model.addAttribute("rows", rows);
        model.addAttribute("status", status);
        model.addAttribute("turbineId", turbineId);
        return "icing-alerts";
    }

    /** 预警导出（CSV，UTF-8 BOM 供 Excel 直接打开） */
    @GetMapping("/icing/alerts/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) IcingAlertStatus status,
                                         @RequestParam(required = false) Long turbineId) {
        List<IcingWarningRow> rows = alertService.listWarningRows(status, turbineId);
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM 供 Excel 直接正确识别编码
        sb.append('\uFEFF').append(String.join(",", IcingAlertService.CSV_HEADERS)).append("\r\n");
        for (IcingWarningRow row : rows) {
            sb.append(toCsvLine(row)).append("\r\n");
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=icing-alerts.csv");
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }

    /** 与 {@link IcingAlertService#CSV_HEADERS} 逐列对应，保证导出列与页面/后台同字段 */
    private String toCsvLine(IcingWarningRow row) {
        IcingAlert a = row.getAlert();
        List<String> cells = new ArrayList<>();
        cells.add(a.getAlertNo());
        cells.add(row.getTurbineNo());
        cells.add(row.getBladeNo());
        cells.add(row.getObservedAt() != null ? FMT.format(row.getObservedAt()) : "");
        cells.add(a.getRiskLevel().name());
        cells.add(String.valueOf(a.getRiskScore()));
        cells.add(String.valueOf(a.getTempScore()));
        cells.add(String.valueOf(a.getHumidityScore()));
        cells.add(String.valueOf(a.getWindScore()));
        cells.add(String.valueOf(a.getVibrationScore()));
        cells.add("v" + a.getParamVersionNo());
        cells.add(a.getStatus().name());
        cells.add(a.getShutdownAdvice() != null ? a.getShutdownAdvice() : "");
        cells.add(a.getShutdownConfirmedBy() != null ? a.getShutdownConfirmedBy() : "");
        cells.add(a.getShutdownConfirmedAt() != null ? FMT.format(a.getShutdownConfirmedAt()) : "");
        cells.add(a.getResolvedBy() != null ? a.getResolvedBy() : "");
        cells.add(a.getResolvedAt() != null ? FMT.format(a.getResolvedAt()) : "");
        return cells.stream().map(IcingViewController::escape).reduce((x, y) -> x + "," + y).orElse("");
    }

    private static String escape(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
