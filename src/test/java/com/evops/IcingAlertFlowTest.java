package com.evops;

import com.evops.dto.BladeRequest;
import com.evops.dto.IcingModelParamRequest;
import com.evops.dto.IcingObservationRequest;
import com.evops.dto.IcingResolveRequest;
import com.evops.dto.IcingShutdownConfirmRequest;
import com.evops.dto.IcingSupplementRequest;
import com.evops.dto.IcingWarningRow;
import com.evops.dto.TurbineCreateRequest;
import com.evops.entity.Blade;
import com.evops.entity.IcingAlert;
import com.evops.entity.IcingModelParam;
import com.evops.entity.IcingNotification;
import com.evops.entity.IcingObservation;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingObservationStatus;
import com.evops.enums.IcingRiskLevel;
import com.evops.enums.VersionStatus;
import com.evops.service.BladeService;
import com.evops.service.IcingAlertService;
import com.evops.service.IcingModelParamService;
import com.evops.service.TurbineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
 * F6 叶片结冰预警：温度/湿度/风速/历史振动分级、停机建议通知值班人员、
 * 两次人工确认、模型参数版本化历史告警不重算、异常缺失待补、
 * 叶片置无效后页面/导出/后台同口径。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IcingAlertFlowTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private BladeService bladeService;
    @Autowired
    private IcingAlertService alertService;
    @Autowired
    private IcingModelParamService paramService;
    @Autowired
    private MockMvc mockMvc;

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 7, 8, 0);

    /** 建一个带一片 A 叶位在役叶片的风机，返回叶片 */
    private Blade setupBlade(String turbineNo) {
        TurbineCreateRequest req = new TurbineCreateRequest();
        req.setTurbineNo(turbineNo);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        BladeRequest blade = new BladeRequest();
        blade.setBladeNo(turbineNo + "-A1");
        blade.setSerialNo("SN-" + turbineNo + "-A1");
        blade.setBladePosition(BladePosition.A);
        blade.setMaterial("玻璃纤维增强复合材料");
        req.setBlades(Arrays.asList(blade));
        Turbine turbine = turbineService.create(req);
        return bladeService.listInServiceAt(turbine.getId(), T0).get(0);
    }

    private IcingObservationRequest obsReq(Blade blade, int seq,
                                           Double temp, Double humidity, Double wind, Double vibration) {
        IcingObservationRequest req = new IcingObservationRequest();
        req.setBladeId(blade.getId());
        req.setObservedAt(T0.plusMinutes(seq));
        req.setTemperatureC(temp);
        req.setHumidityPct(humidity);
        req.setWindSpeedMs(wind);
        req.setVibration(vibration);
        req.setOperator("值班调度");
        return req;
    }

    /** 取某叶片最新一条告警 */
    private IcingAlert latestAlert(Long bladeId) {
        return alertService.listWarningRows(null, null).stream()
                .map(IcingWarningRow::getAlert)
                .filter(a -> a.getBladeId().equals(bladeId))
                .findFirst().orElse(null);
    }

    @Test
    void 风险分级_低风险不告警_中高风险通知值班人员并给停机建议() {
        Blade blade = setupBlade("T-F6-01");

        // 低风险：温度高于结冰敏感值、湿度/风速/振动均低 → 不告警
        IcingObservation low = alertService.report(obsReq(blade, 1, 10.0, 50.0, 2.0, 1.0));
        assertEquals(IcingObservationStatus.OK, low.getStatus());
        assertNull(latestAlert(blade.getId()));

        // 一般风险（MEDIUM，风险分约 72）：预警通知，无停机建议
        alertService.report(obsReq(blade, 2, -10.0, 90.0, 15.0, 7.0));
        IcingAlert medium = latestAlert(blade.getId());
        assertNotNull(medium);
        assertEquals(IcingRiskLevel.MEDIUM, medium.getRiskLevel());
        assertEquals(IcingAlertStatus.ACTIVE, medium.getStatus());
        assertNull(medium.getShutdownAdvice());
        List<IcingNotification> mediumNotes = alertService.listNotifications(medium.getId());
        assertEquals(1, mediumNotes.size());
        assertEquals("值班调度", mediumNotes.get(0).getRecipient());

        // 高风险：四项均饱和 → 风险分 100，生成停机建议并再次通知
        alertService.report(obsReq(blade, 3, -18.0, 95.0, 18.0, 8.0));
        IcingAlert high = latestAlert(blade.getId());
        assertEquals(IcingRiskLevel.HIGH, high.getRiskLevel());
        assertEquals(100, high.getRiskScore());
        assertNotNull(high.getShutdownAdvice());
        assertTrue(high.getShutdownAdvice().contains("停机"));
        List<IcingNotification> highNotes = alertService.listNotifications(high.getId());
        assertEquals(1, highNotes.size());
        assertTrue(highNotes.get(0).getContent().contains("停机建议"));
        // 快照分项与参数版本
        assertEquals(100, high.getTempScore());
        assertEquals(100, high.getHumidityScore());
        assertEquals(1, high.getParamVersionNo());
    }

    @Test
    void 两次人工确认_高风险确认停机后才能解除_不可越级_不可重复() {
        Blade blade = setupBlade("T-F6-02");
        alertService.report(obsReq(blade, 1, -18.0, 95.0, 18.0, 8.0));
        IcingAlert high = latestAlert(blade.getId());

        // 未确认停机前不能直接解除
        IcingResolveRequest resolve = new IcingResolveRequest();
        resolve.setResolvedBy("场长-钱七");
        Long id = high.getId();
        assertThrows(IllegalArgumentException.class, () -> alertService.confirmResolve(id, resolve));

        // 人工确认停机建议后才执行停机
        IcingShutdownConfirmRequest shutdown = new IcingShutdownConfirmRequest();
        shutdown.setConfirmedBy("值班调度-孙八");
        shutdown.setRemark("现场核实叶片覆冰，执行停机");
        high = alertService.confirmShutdown(id, shutdown);
        assertEquals(IcingAlertStatus.STOP_CONFIRMED, high.getStatus());
        assertNotNull(high.getShutdownConfirmedAt());

        // 已确认停机不能重复确认
        assertThrows(IllegalArgumentException.class, () -> alertService.confirmShutdown(id, shutdown));

        // 解除风险须再次确认
        high = alertService.confirmResolve(id, resolve);
        assertEquals(IcingAlertStatus.RESOLVED, high.getStatus());
        assertNotNull(high.getResolvedAt());
        // 已解除不可重复确认
        assertThrows(IllegalArgumentException.class, () -> alertService.confirmResolve(id, resolve));
    }

    @Test
    void 一般预警_未停机_可直接确认解除() {
        Blade blade = setupBlade("T-F6-03");
        alertService.report(obsReq(blade, 1, -10.0, 90.0, 15.0, 7.0));
        IcingAlert medium = latestAlert(blade.getId());
        // MEDIUM 无停机建议，确认停机被拒
        IcingShutdownConfirmRequest shutdown = new IcingShutdownConfirmRequest();
        shutdown.setConfirmedBy("值班调度-孙八");
        Long id = medium.getId();
        assertThrows(IllegalArgumentException.class, () -> alertService.confirmShutdown(id, shutdown));
        // 可直接确认解除
        IcingResolveRequest resolve = new IcingResolveRequest();
        resolve.setResolvedBy("场长-钱七");
        assertEquals(IcingAlertStatus.RESOLVED, alertService.confirmResolve(id, resolve).getStatus());
    }

    @Test
    void 异常与缺失数据_标记待补_补传后按原参数版本重算() {
        Blade blade = setupBlade("T-F6-04");

        // 历史振动缺失 → 待补，不生成告警
        IcingObservation missingVib = alertService.report(obsReq(blade, 1, -18.0, 95.0, 18.0, null));
        assertEquals(IcingObservationStatus.PENDING_SUPPLEMENT, missingVib.getStatus());
        assertTrue(missingVib.getMissingFields().contains("vibration"));
        assertNull(latestAlert(blade.getId()));

        // 异常值（温度越界）→ 同样待补
        IcingObservation abnormal = alertService.report(obsReq(blade, 2, 999.0, 95.0, 18.0, 8.0));
        assertEquals(IcingObservationStatus.PENDING_SUPPLEMENT, abnormal.getStatus());
        assertTrue(abnormal.getMissingFields().contains("temperatureC"));

        // 参数先调整到 v2：待补观测仍沿用首次上报时的 v1
        IcingModelParam v1 = paramService.mustGetCurrent();
        paramService.adjust(v2Request());
        assertEquals(2, paramService.mustGetCurrent().getVersionNo());

        // 补齐振动：按 v1 重算 → 高风险告警，固化 v1
        IcingSupplementRequest supplement = new IcingSupplementRequest();
        supplement.setVibration(8.0);
        supplement.setOperator("测点补传-周九");
        missingVib = alertService.supplement(missingVib.getId(), supplement);
        assertEquals(IcingObservationStatus.OK, missingVib.getStatus());
        assertEquals(2, missingVib.getRevision());
        assertEquals(v1.getId(), missingVib.getParamVersionId());
        IcingAlert high = latestAlert(blade.getId());
        assertNotNull(high);
        assertEquals(IcingRiskLevel.HIGH, high.getRiskLevel());
        assertEquals(1, high.getParamVersionNo());

        // 非待补观测不能补传
        final Long supplementedId = missingVib.getId();
        assertThrows(IllegalArgumentException.class,
                () -> alertService.supplement(supplementedId, supplement));
    }

    @Test
    void 参数版本化_调整只生成新版本_历史告警不随新参数重算() {
        Blade blade = setupBlade("T-F6-05");
        // v1 下的高风险告警
        alertService.report(obsReq(blade, 1, -18.0, 95.0, 18.0, 8.0));
        IcingAlert old = latestAlert(blade.getId());
        assertEquals(1, old.getParamVersionNo());
        int oldScore = old.getRiskScore();

        // 调整参数生成 v2，v1 退役但内容不改
        IcingModelParam v2 = paramService.adjust(v2Request());
        assertEquals(VersionStatus.PUBLISHED, v2.getStatus());
        List<IcingModelParam> versions = paramService.listVersions();
        assertEquals(2, versions.size());
        assertEquals(VersionStatus.RETIRED, versions.get(0).getStatus());

        // 历史告警不随新参数重算：分数、分项、参数版本快照不变
        IcingAlert oldAfter = alertService.mustGetAlert(old.getId());
        assertEquals(1, oldAfter.getParamVersionNo());
        assertEquals(oldScore, oldAfter.getRiskScore());
        assertEquals(IcingRiskLevel.HIGH, oldAfter.getRiskLevel());

        // 新观测按 v2 计算：v2 只看振动且起算 100，原输入振动 8 → 0 分，不再告警
        alertService.report(obsReq(blade, 2, -18.0, 95.0, 18.0, 8.0));
        List<IcingAlert> bladeAlerts = alertService.listWarningRows(null, null).stream()
                .map(IcingWarningRow::getAlert)
                .filter(a -> a.getBladeId().equals(blade.getId()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(1, bladeAlerts.size());
        assertEquals(old.getId(), bladeAlerts.get(0).getId());
    }

    @Test
    void 叶片置无效后_页面导出后台同口径不再统计() throws Exception {
        Blade blade = setupBlade("T-F6-06");
        alertService.report(obsReq(blade, 1, -18.0, 95.0, 18.0, 8.0));
        IcingAlert high = latestAlert(blade.getId());
        String alertNo = high.getAlertNo();

        // 置无效前：后台列表、页面、导出三处同口径都包含该预警
        List<IcingWarningRow> rows = alertService.listWarningRows(null, null);
        assertTrue(rows.stream().anyMatch(r -> r.getAlert().getAlertNo().equals(alertNo)));
        String pageHtml = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/icing/alerts/page"))
                .andReturn().getResponse().getContentAsString();
        String csv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/icing/alerts/export"))
                .andReturn().getResponse().getContentAsString();
        String apiJson = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/icing/alerts"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pageHtml.contains(alertNo));
        assertTrue(csv.contains(alertNo));
        assertTrue(apiJson.contains(alertNo));
        // 导出与页面同字段：CSV 表头口径
        assertTrue(csv.contains("预警编号") && csv.contains("参数版本"));

        // 将风电叶片运维对象置为无效
        bladeService.invalidate(blade.getId(), "叶片拆除报废", "资产管理员-吴十");
        Blade invalid = bladeService.mustGet(blade.getId());
        assertFalse(invalid.getValid());
        assertNotNull(invalid.getInvalidatedAt());

        // 置无效后：后台列表、页面、导出三处同口径都不再包含（历史告警仍在库可查）
        assertFalse(alertService.listWarningRows(null, null).stream()
                .anyMatch(r -> r.getAlert().getAlertNo().equals(alertNo)));
        pageHtml = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/icing/alerts/page"))
                .andReturn().getResponse().getContentAsString();
        csv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/icing/alerts/export"))
                .andReturn().getResponse().getContentAsString();
        apiJson = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/icing/alerts"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(pageHtml.contains(alertNo));
        assertFalse(csv.contains(alertNo));
        assertFalse(apiJson.contains(alertNo));
        // 历史告警不删除
        assertEquals(IcingRiskLevel.HIGH, alertService.mustGetAlert(high.getId()).getRiskLevel());

        // 无效叶片不再接受观测上报
        IcingObservationRequest req = obsReq(blade, 9, -18.0, 95.0, 18.0, 8.0);
        assertThrows(IllegalArgumentException.class, () -> alertService.report(req));
    }

    /** v2：仅保留振动权重且起算抬到 100，使常规观测风险分为 0 */
    private IcingModelParamRequest v2Request() {
        IcingModelParamRequest req = new IcingModelParamRequest();
        req.setTempWeight(0.0);
        req.setHumidityWeight(0.0);
        req.setWindWeight(0.0);
        req.setVibrationWeight(1.0);
        req.setTempCutoffC(2.0);
        req.setHumidityLowPct(70.0);
        req.setHumidityHighPct(95.0);
        req.setWindLowMs(6.0);
        req.setWindHighMs(18.0);
        req.setVibrationLow(100.0);
        req.setVibrationHigh(200.0);
        req.setVibrationWindowHours(24);
        req.setWarnThreshold(60);
        req.setStopThreshold(80);
        req.setOperator("总工-赵六");
        return req;
    }
}
