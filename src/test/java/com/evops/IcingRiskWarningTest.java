package com.evops;

import com.evops.controller.IcingPageController;
import com.evops.dto.BladeRequest;
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
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.ConfirmationResult;
import com.evops.enums.DataQualityFlag;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingConfirmType;
import com.evops.enums.IcingRiskLevel;
import com.evops.enums.TurbineRunStatus;
import com.evops.enums.TurbineStatus;
import com.evops.enums.VersionStatus;
import com.evops.service.IcingAlertService;
import com.evops.service.IcingModelService;
import com.evops.service.TurbineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F6 叶片结冰风险预警：
 * 四因子阈值评分、停机建议与值班通知、停机/解除两次人工确认、
 * 模型参数版本化（历史告警不随新参数重算）、缺失/异常数据标记待补与补传重算、
 * 风机置无效后页面/导出/后台同口径。
 */
@SpringBootTest
@Transactional
class IcingRiskWarningTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private IcingAlertService alertService;
    @Autowired
    private IcingModelService modelService;
    @Autowired
    private IcingPageController pageController;

    private Turbine createTurbine(String no) {
        com.evops.dto.TurbineCreateRequest req = new com.evops.dto.TurbineCreateRequest();
        req.setTurbineNo(no);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        BladeRequest blade = new BladeRequest();
        blade.setBladeNo(no + "-B1");
        blade.setSerialNo("SN-" + no + "-1");
        blade.setBladePosition(BladePosition.A);
        blade.setMaterial("玻璃纤维增强复合材料");
        req.setBlades(Arrays.asList(blade));
        return turbineService.create(req);
    }

    private IcingMonitorRequest monitor(Long turbineId, LocalDateTime time,
                                        Double temp, Double humidity, Double wind, Double vibration) {
        IcingMonitorRequest req = new IcingMonitorRequest();
        req.setTurbineId(turbineId);
        req.setMonitorTime(time);
        req.setTemperature(temp);
        req.setHumidityPct(humidity);
        req.setWindSpeed(wind);
        req.setVibration(vibration);
        return req;
    }

    private IcingConfirmRequest confirm(String person, String opinion) {
        IcingConfirmRequest req = new IcingConfirmRequest();
        req.setConfirmer(person);
        req.setOpinion(opinion);
        return req;
    }

    @Test
    void 四因子评分_达到阈值生成告警停机建议并通知值班人员() {
        Turbine turbine = createTurbine("WT-F601");

        // 常温常湿、适宜风速、振动贴基线 → 正常，不告警
        IcingEvaluateResult normal = alertService.report(monitor(turbine.getId(),
                LocalDateTime.of(2026, 12, 1, 7, 0), 10.0, 60.0, 8.0, 0.20));
        assertFalse(normal.isPendingSupplement());
        assertEquals(IcingRiskLevel.NORMAL, normal.getScore().getLevel());
        assertNull(normal.getAlert());

        // -10℃ / 95% / 2m·s⁻¹（静稳）/ 振动0.30g（偏离基线50%）→ 94 分，危急
        IcingEvaluateResult critical = alertService.report(monitor(turbine.getId(),
                LocalDateTime.of(2026, 12, 1, 8, 0), -10.0, 95.0, 2.0, 0.30));
        assertEquals(94, critical.getScore().getTotal());
        assertEquals(40, critical.getScore().getTempScore());
        assertEquals(19, critical.getScore().getHumidityScore());
        assertEquals(15, critical.getScore().getWindScore());
        assertEquals(20, critical.getScore().getVibrationScore());
        assertEquals(IcingRiskLevel.CRITICAL, critical.getScore().getLevel());

        IcingAlert alert = critical.getAlert();
        assertNotNull(alert);
        assertEquals(IcingAlertStatus.ACTIVE, alert.getStatus());
        assertEquals(IcingRiskLevel.CRITICAL, alert.getRiskLevel());
        // 固化产生时的模型版本
        assertEquals(1, alert.getModelVersionNo());
        assertTrue(alert.getShutdownAdvice().contains("停机"));

        // 通知值班人员并留回执
        IcingAlertDetail detail = alertService.getAlertDetail(alert.getId());
        assertEquals(1, detail.getNotifications().size());
        assertEquals("值班调度员", detail.getNotifications().get(0).getRecipient());
        assertTrue(detail.getNotifications().get(0).getContent().contains("停机建议"));
        assertNotNull(detail.getNotifications().get(0).getReceiptNo());

        // 持续超阈值的重复上报不重复告警、不重复通知
        IcingEvaluateResult stillCritical = alertService.report(monitor(turbine.getId(),
                LocalDateTime.of(2026, 12, 1, 8, 10), -11.0, 96.0, 2.0, 0.31));
        assertEquals(IcingRiskLevel.CRITICAL, stillCritical.getScore().getLevel());
        assertNull(stillCritical.getAlert());
        assertEquals(1, alertService.getAlertDetail(alert.getId()).getNotifications().size());
    }

    @Test
    void 人工确认才停机_解除风险需再次确认_解除驳回可再确认() {
        Turbine turbine = createTurbine("WT-F602");
        IcingAlert alert = alertService.report(monitor(turbine.getId(),
                LocalDateTime.of(2026, 12, 1, 8, 0), -10.0, 95.0, 2.0, 0.30)).getAlert();
        assertNotNull(alert);

        // 未确认停机前不能解除
        assertThrows(IllegalArgumentException.class,
                () -> alertService.confirmClear(alert.getId(), true, confirm("值班-钱七", null)));
        // 系统不自动停机：确认前仍为预警中
        assertEquals(IcingAlertStatus.ACTIVE, alertService.mustGetAlert(alert.getId()).getStatus());

        // 第一次人工确认：停机
        IcingAlert afterShutdown = alertService.confirmShutdown(alert.getId(), confirm("值班-钱七", "现场覆冰明显"));
        assertEquals(IcingAlertStatus.SHUTDOWN_CONFIRMED, afterShutdown.getStatus());
        assertEquals("值班-钱七", afterShutdown.getShutdownConfirmedBy());
        assertNotNull(afterShutdown.getShutdownConfirmedAt());

        // 已确认停机后不能重复停机确认
        assertThrows(IllegalArgumentException.class,
                () -> alertService.confirmShutdown(alert.getId(), confirm("值班-钱七", null)));

        // 解除驳回必须填原因
        assertThrows(IllegalArgumentException.class,
                () -> alertService.confirmClear(alert.getId(), false, confirm("检修-孙八", null)));

        // 第二次确认被驳回：维持已停机、风险未解除，可再次确认
        IcingAlert rejected = alertService.confirmClear(alert.getId(), false, confirm("检修-孙八", "叶尖仍有残冰"));
        assertEquals(IcingAlertStatus.SHUTDOWN_CONFIRMED, rejected.getStatus());
        assertNull(rejected.getClearConfirmedAt());

        // 再次确认解除：闭环
        IcingAlert cleared = alertService.confirmClear(alert.getId(), true, confirm("检修-孙八", "覆冰清除，复核平衡正常"));
        assertEquals(IcingAlertStatus.CLEARED, cleared.getStatus());
        assertEquals("检修-孙八", cleared.getClearConfirmedBy());
        assertNotNull(cleared.getClearConfirmedAt());

        // 已闭环不能再确认
        assertThrows(IllegalArgumentException.class,
                () -> alertService.confirmShutdown(alert.getId(), confirm("值班-钱七", null)));

        // 确认记录逐条留痕：停机PASS、解除FAIL、解除PASS
        List<com.evops.entity.IcingAlertConfirmation> records =
                alertService.getAlertDetail(alert.getId()).getConfirmations();
        assertEquals(3, records.size());
        assertEquals(IcingConfirmType.SHUTDOWN, records.get(0).getConfirmType());
        assertEquals(ConfirmationResult.PASS, records.get(0).getDecision());
        assertEquals(IcingConfirmType.CLEAR, records.get(1).getConfirmType());
        assertEquals(ConfirmationResult.FAIL, records.get(1).getDecision());
        assertEquals(ConfirmationResult.PASS, records.get(2).getDecision());

        // 解除闭环后，再次进入结冰条件可产生新一轮告警
        IcingEvaluateResult nextRound = alertService.report(monitor(turbine.getId(),
                LocalDateTime.of(2026, 12, 2, 6, 0), -12.0, 97.0, 1.0, 0.32));
        assertNotNull(nextRound.getAlert());
    }

    @Test
    void 模型参数版本化_历史告警不随新参数重算() {
        Turbine turbine = createTurbine("WT-F603");

        // v1 下产生危急告警，得分 94，固化 v1
        IcingAlert v1Alert = alertService.report(monitor(turbine.getId(),
                LocalDateTime.of(2026, 12, 1, 8, 0), -10.0, 95.0, 2.0, 0.30)).getAlert();
        assertNotNull(v1Alert);
        IcingModelVersion v1 = modelService.mustGetCurrent();
        assertEquals(1, v1.getVersionNo());
        assertEquals(v1.getId(), v1Alert.getModelVersionId());
        assertEquals(94, v1Alert.getScore());

        // 走完两次确认，闭环旧告警
        alertService.confirmShutdown(v1Alert.getId(), confirm("值班-钱七", null));
        alertService.confirmClear(v1Alert.getId(), true, confirm("检修-孙八", "已除冰"));

        // 参数调整：生成 v2 并发布，v1 退役但内容不改
        IcingModelVersion v2 = modelService.adjust(stricterModelV2());
        assertEquals(2, v2.getVersionNo());
        assertEquals(VersionStatus.PUBLISHED, v2.getStatus());
        List<IcingModelVersion> versions = modelService.listVersions();
        assertEquals(2, versions.size());
        IcingModelVersion v1After = versions.get(0);
        assertEquals(VersionStatus.RETIRED, v1After.getStatus());
        assertEquals(2.0, v1After.getTempThreshold());
        assertEquals(60, v1After.getAlertThreshold());

        // 历史告警不随新参数重算：仍固化 v1、94 分、危急
        IcingAlert oldAlert = alertService.mustGetAlert(v1Alert.getId());
        assertEquals(v1.getId(), oldAlert.getModelVersionId());
        assertEquals(1, oldAlert.getModelVersionNo());
        assertEquals(94, oldAlert.getScore());
        assertEquals(IcingRiskLevel.CRITICAL, oldAlert.getRiskLevel());
        IcingAlertDetail oldDetail = alertService.getAlertDetail(oldAlert.getId());
        assertEquals(1, oldDetail.getAlert().getModelVersionNo());

        // 同一组读数在 v2 下只评得 65 分、预警——证明新数据用新版本，旧告警没有被重算
        IcingEvaluateResult underV2 = alertService.report(monitor(turbine.getId(),
                LocalDateTime.of(2026, 12, 1, 9, 0), -10.0, 95.0, 2.0, 0.30));
        assertEquals(65, underV2.getScore().getTotal());
        assertEquals(IcingRiskLevel.WARNING, underV2.getScore().getLevel());
        assertEquals(v2.getId(), underV2.getModelVersion().getId());
        IcingAlert v2Alert = underV2.getAlert();
        assertNotNull(v2Alert);
        assertEquals(2, v2Alert.getModelVersionNo());
        assertEquals(65, v2Alert.getScore());
        assertEquals(IcingRiskLevel.WARNING, v2Alert.getRiskLevel());
    }

    private IcingModelAdjustRequest stricterModelV2() {
        IcingModelAdjustRequest req = new IcingModelAdjustRequest();
        req.setTempThreshold(0.0);
        req.setTempFullBandC(10.0);
        req.setTempScoreMax(30);
        req.setHumidityThreshold(85.0);
        req.setHumidityFullBandPct(15.0);
        req.setHumidityScoreMax(20);
        req.setWindSpeedMin(4.0);
        req.setWindSpeedMax(16.0);
        req.setWindScoreMax(10);
        req.setVibrationBaseline(0.20);
        req.setVibrationAbnormalRatio(0.60);
        req.setVibrationScoreMax(15);
        req.setAlertThreshold(60);
        req.setCriticalThreshold(85);
        req.setShutdownAdvice("v2：建议降功率并远程停机核查覆冰");
        req.setOperator("总工-赵六");
        return req;
    }

    @Test
    void 异常或缺失数据标记待补_补传后重算并递增版本号() {
        Turbine turbine = createTurbine("WT-F604");
        LocalDateTime time = LocalDateTime.of(2026, 12, 1, 8, 0);

        // 温度缺失 + 湿度越界异常 → 标记待补，不评分不告警
        IcingEvaluateResult missing = alertService.report(monitor(turbine.getId(), time,
                null, 150.0, 2.0, 0.30));
        assertTrue(missing.isPendingSupplement());
        List<IcingMonitorData> pendingList = alertService.listMonitorData(turbine.getId(), true);
        assertEquals(1, pendingList.size());
        IcingMonitorData pending = pendingList.get(0);
        assertEquals(DataQualityFlag.DATA_MISSING, pending.getQualityFlag());
        assertTrue(pending.getMissingFields().contains("temperature"));
        assertTrue(pending.getMissingFields().contains("humidityPct"));
        assertNull(pending.getScore());
        assertNull(pending.getRiskLevel());
        assertNull(pending.getModelVersionId());
        assertEquals(1, pending.getRevision());

        // 补传（同风机同时刻，幂等归并）：完整读数 → 重算为危急并生成告警，revision 递增
        IcingEvaluateResult supplemented = alertService.report(monitor(turbine.getId(), time,
                -10.0, 95.0, 2.0, 0.30));
        assertFalse(supplemented.isPendingSupplement());
        assertEquals(IcingRiskLevel.CRITICAL, supplemented.getScore().getLevel());
        assertNotNull(supplemented.getAlert());

        List<IcingMonitorData> after = alertService.listMonitorData(turbine.getId(), false);
        assertEquals(1, after.size());
        IcingMonitorData filled = after.get(0);
        assertEquals(DataQualityFlag.NORMAL, filled.getQualityFlag());
        assertNull(filled.getMissingFields());
        assertEquals(94, filled.getScore());
        assertEquals(2, filled.getRevision());
        // 待补列表已清空
        assertEquals(0, alertService.listMonitorData(turbine.getId(), true).size());
    }

    @Test
    void 风机置无效后_页面导出后台同口径排除() {
        Turbine a = createTurbine("WT-F610");
        Turbine b = createTurbine("WT-F611");
        LocalDateTime t = LocalDateTime.of(2026, 12, 1, 8, 0);
        IcingAlert alertA = alertService.report(monitor(a.getId(), t, -10.0, 95.0, 2.0, 0.30)).getAlert();
        IcingAlert alertB = alertService.report(monitor(b.getId(), t, -10.0, 95.0, 2.0, 0.30)).getAlert();
        assertNotNull(alertA);
        assertNotNull(alertB);

        IcingAlertFilter empty = new IcingAlertFilter();
        // 置无效前：后台口径两台都在
        List<IcingAlertRow> rowsBefore = alertService.listAlerts(empty, false);
        assertTrue(rowsBefore.stream().anyMatch(r -> r.getTurbineNo().equals("WT-F610")));
        assertTrue(rowsBefore.stream().anyMatch(r -> r.getTurbineNo().equals("WT-F611")));

        // 将风电叶片运维对象 A 置为无效：不删除档案，闭合服役窗口
        Turbine deactivated = turbineService.deactivate(a.getId(), "系统管理员");
        assertEquals(TurbineRunStatus.INVALID, deactivated.getRunStatus());
        assertEquals(TurbineStatus.DECOMMISSIONED, deactivated.getStatus());
        assertNotNull(deactivated.getDecommissionedAt());

        // 无效对象不再接受结冰监测上报
        assertThrows(IllegalArgumentException.class,
                () -> alertService.report(monitor(a.getId(),
                        LocalDateTime.of(2026, 12, 1, 10, 0), -10.0, 95.0, 2.0, 0.30)));

        // 后台口径：排除 A，保留 B；按 A 过滤查不到任何记录
        List<IcingAlertRow> rowsAfter = alertService.listAlerts(empty, false);
        assertFalse(rowsAfter.stream().anyMatch(r -> r.getTurbineNo().equals("WT-F610")));
        assertTrue(rowsAfter.stream().anyMatch(r -> r.getTurbineNo().equals("WT-F611")));
        IcingAlertFilter onlyA = new IcingAlertFilter();
        onlyA.setTurbineId(a.getId());
        assertEquals(0, alertService.listAlerts(onlyA, false).size());

        // 页面对象（Thymeleaf 控制器）走同一 service 方法：同口径排除 A
        ExtendedModelMap model = new ExtendedModelMap();
        String view = pageController.page(null, null, null, model);
        assertEquals("icing-alerts", view);
        @SuppressWarnings("unchecked")
        List<IcingAlertRow> pageRows = (List<IcingAlertRow>) model.get("rows");
        assertFalse(pageRows.stream().anyMatch(r -> r.getTurbineNo().equals("WT-F610")));
        assertTrue(pageRows.stream().anyMatch(r -> r.getTurbineNo().equals("WT-F611")));

        // 导出 CSV 与页面、后台同口径：不含 A、含 B
        byte[] csv = pageController.export(null, null, null).getBody();
        assertNotNull(csv);
        String csvText = new String(csv, StandardCharsets.UTF_8);
        assertTrue(csvText.contains("WT-F611"));
        assertFalse(csvText.contains("WT-F610"));
        // 导出内容携带风险等级与停机建议列
        assertTrue(csvText.contains("CRITICAL"));
        assertTrue(csvText.contains("停机"));

        // 历史告警本体仍可按编号追溯（仅列表三口径排除，档案不删除）
        assertEquals(IcingAlertStatus.ACTIVE, alertService.mustGetAlert(alertA.getId()).getStatus());
    }
}
