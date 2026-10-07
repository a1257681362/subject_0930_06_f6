package com.evops;

import com.evops.dto.ConfirmWeatherRequest;
import com.evops.dto.EquipmentRequest;
import com.evops.dto.MaintenanceTaskCreateRequest;
import com.evops.dto.PostponeRequest;
import com.evops.dto.ShiftDetailDto;
import com.evops.dto.TurbineCreateRequest;
import com.evops.dto.WeatherWindowRequest;
import com.evops.dto.WorkerRequest;
import com.evops.entity.Equipment;
import com.evops.entity.MaintenanceTask;
import com.evops.entity.ShiftAssignment;
import com.evops.entity.ShiftNotification;
import com.evops.entity.ShiftPostpone;
import com.evops.entity.Turbine;
import com.evops.entity.WeatherConfirmation;
import com.evops.entity.WeatherWindow;
import com.evops.entity.WorkShift;
import com.evops.entity.Worker;
import com.evops.enums.AssignType;
import com.evops.enums.ConfirmationResult;
import com.evops.enums.EquipmentStatus;
import com.evops.enums.LightningRisk;
import com.evops.enums.MaintenanceTaskStatus;
import com.evops.enums.NotifyType;
import com.evops.enums.ShiftStatus;
import com.evops.service.EquipmentService;
import com.evops.service.MaintenanceTaskService;
import com.evops.service.TurbineService;
import com.evops.service.WeatherWindowService;
import com.evops.service.WorkShiftService;
import com.evops.service.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 高空作业排班：天气窗口幂等归并、候选班次生成、收窄预警、二次确认、延期回执、资质校验 */
@SpringBootTest
@Transactional
class HighAltitudeSchedulingTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private WeatherWindowService weatherWindowService;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private EquipmentService equipmentService;
    @Autowired
    private MaintenanceTaskService maintenanceTaskService;
    @Autowired
    private WorkShiftService workShiftService;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 8, 8, 0);

    private Turbine turbine(String no) {
        TurbineCreateRequest req = new TurbineCreateRequest();
        req.setTurbineNo(no);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return turbineService.create(req);
    }

    private WeatherWindowRequest windowReq(Turbine turbine, LocalDateTime start, int hours,
                                           double wind, double rain, LightningRisk lightning) {
        WeatherWindowRequest req = new WeatherWindowRequest();
        req.setTurbineId(turbine.getId());
        req.setWindowStart(start);
        req.setWindowEnd(start.plusHours(hours));
        req.setMaxWindSpeed(wind);
        req.setRainfallMm(rain);
        req.setLightningRisk(lightning);
        req.setSource("省气象台");
        return req;
    }

    private Worker worker(String no, String qualification, LocalDateTime validUntil) {
        WorkerRequest req = new WorkerRequest();
        req.setWorkerNo(no);
        req.setName("张三" + no);
        req.setQualificationType(qualification);
        req.setQualificationValidUntil(validUntil);
        return workerService.register(req);
    }

    private Equipment equipment(String no, String type, LocalDateTime from, LocalDateTime to) {
        EquipmentRequest req = new EquipmentRequest();
        req.setEquipmentNo(no);
        req.setName("装备" + no);
        req.setEquipmentType(type);
        req.setAvailableFrom(from);
        req.setAvailableTo(to);
        return equipmentService.register(req);
    }

    private MaintenanceTask task(Turbine turbine, String no, int durationHours, int workers, String equipmentType) {
        MaintenanceTaskCreateRequest req = new MaintenanceTaskCreateRequest();
        req.setTaskNo(no);
        req.setTurbineId(turbine.getId());
        req.setRequiredQualification("HIGH_ALTITUDE");
        req.setRequiredWorkers(workers);
        req.setRequiredEquipmentType(equipmentType);
        req.setEstimatedDurationHours(durationHours);
        return maintenanceTaskService.create(req);
    }

    private ConfirmWeatherRequest weather(double wind, double rain, LightningRisk lightning) {
        ConfirmWeatherRequest req = new ConfirmWeatherRequest();
        req.setWindSpeed(wind);
        req.setRainfallMm(rain);
        req.setLightningRisk(lightning);
        req.setConfirmedBy("值班员甲");
        return req;
    }

    private List<ShiftAssignment> assignments(Long shiftId, AssignType type) {
        return workShiftService.detail(shiftId).getAssignments().stream()
                .filter(a -> a.getAssignType() == type)
                .collect(Collectors.toList());
    }

    @Test
    void 天气窗口补传补录重试_只形成一条可用结果() {
        Turbine turbine = turbine("T401");
        WeatherWindow first = weatherWindowService.report(
                windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));
        assertEquals(1, first.getRevision());

        // 补传：同一风机同一起止，读数更新
        WeatherWindowRequest resend = windowReq(turbine, BASE, 4, 9.5, 0.0, LightningRisk.NONE);
        resend.setSource("补传");
        WeatherWindow merged = weatherWindowService.report(resend);
        // 重试：再次上报同一窗口
        WeatherWindow retried = weatherWindowService.report(
                windowReq(turbine, BASE, 4, 9.0, 0.0, LightningRisk.NONE));

        assertEquals(first.getId(), merged.getId());
        assertEquals(first.getId(), retried.getId());
        assertEquals(first.getWindowNo(), retried.getWindowNo());
        List<WeatherWindow> windows = weatherWindowService.listByTurbine(turbine.getId());
        assertEquals(1, windows.size());
        WeatherWindow only = windows.get(0);
        assertEquals(3, only.getRevision());
        assertEquals(9.0, only.getMaxWindSpeed());
    }

    @Test
    void 候选班次生成_结合资质装备与维修时长() {
        Turbine turbine = turbine("T402");
        // 人员：两人资质有效，一人资质过期
        Worker w1 = worker("WK-1", "HIGH_ALTITUDE", BASE.plusDays(10));
        Worker w2 = worker("WK-2", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-3", "HIGH_ALTITUDE", BASE.minusDays(1)); // 已过期
        worker("WK-4", "ELECTRICIAN", BASE.plusDays(10));   // 资质类型不匹配
        // 装备：一台可用，一台维保中
        Equipment e1 = equipment("EQ-1", "升降平台", BASE.minusDays(1), BASE.plusDays(10));
        Equipment e2 = equipment("EQ-2", "升降平台", null, null);
        e2.setStatus(EquipmentStatus.MAINTENANCE);
        equipmentService.updateById(e2);

        // 适窗 4 小时；太短窗口 1 小时；不适窗（风速超限/降雨/雷电）
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));
        weatherWindowService.report(windowReq(turbine, BASE.plusDays(1), 1, 8.0, 0.0, LightningRisk.NONE));
        weatherWindowService.report(windowReq(turbine, BASE.plusDays(2), 4, 15.0, 0.0, LightningRisk.NONE));
        weatherWindowService.report(windowReq(turbine, BASE.plusDays(3), 4, 8.0, 1.0, LightningRisk.NONE));
        weatherWindowService.report(windowReq(turbine, BASE.plusDays(4), 4, 8.0, 0.0, LightningRisk.LOW));

        MaintenanceTask task = task(turbine, "MT-402", 2, 2, "升降平台");
        List<WorkShift> candidates = maintenanceTaskService.generateShifts(task.getId());

        // 只有适窗 4 小时窗口生成候选：班次 2 小时落在窗口内
        assertEquals(1, candidates.size());
        WorkShift candidate = candidates.get(0);
        assertEquals(BASE, candidate.getShiftStart());
        assertEquals(BASE.plusHours(2), candidate.getShiftEnd());
        assertEquals(ShiftStatus.CANDIDATE, candidate.getStatus());

        // 过期资质与类型不匹配人员未被排入
        List<Long> workerIds = assignments(candidate.getId(), AssignType.WORKER).stream()
                .map(ShiftAssignment::getWorkerId).collect(Collectors.toList());
        assertEquals(2, workerIds.size());
        assertTrue(workerIds.contains(w1.getId()));
        assertTrue(workerIds.contains(w2.getId()));
        // 维保中装备未被排入
        List<Long> equipmentIds = assignments(candidate.getId(), AssignType.EQUIPMENT).stream()
                .map(ShiftAssignment::getEquipmentId).collect(Collectors.toList());
        assertEquals(1, equipmentIds.size());
        assertTrue(equipmentIds.contains(e1.getId()));
    }

    @Test
    void 候选班次生成_人数或装备不足则无候选() {
        Turbine turbine = turbine("T403");
        worker("WK-5", "HIGH_ALTITUDE", BASE.plusDays(10)); // 只有 1 人，任务要 2 人
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-403", 2, 2, null);
        assertEquals(0, maintenanceTaskService.generateShifts(task.getId()).size());

        // 装备类型无可用装备
        MaintenanceTask task2 = task(turbine, "MT-403B", 2, 1, "升降平台");
        assertEquals(0, maintenanceTaskService.generateShifts(task2.getId()).size());
    }

    @Test
    void 生成幂等_重试只留一套候选() {
        Turbine turbine = turbine("T404");
        worker("WK-6", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-7", "HIGH_ALTITUDE", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));
        weatherWindowService.report(windowReq(turbine, BASE.plusDays(1), 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-404", 2, 2, null);
        assertEquals(2, maintenanceTaskService.generateShifts(task.getId()).size());
        // 重试/补录后重新生成：候选不堆积
        assertEquals(2, maintenanceTaskService.generateShifts(task.getId()).size());
        assertEquals(2, maintenanceTaskService.detail(task.getId()).getShifts().size());
    }

    @Test
    void 确认班次_唯一可用结果_过期资质拦截() {
        Turbine turbine = turbine("T405");
        worker("WK-8", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-9", "HIGH_ALTITUDE", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));
        weatherWindowService.report(windowReq(turbine, BASE.plusDays(1), 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-405", 2, 2, null);
        List<WorkShift> candidates = maintenanceTaskService.generateShifts(task.getId());
        assertEquals(2, candidates.size());
        WorkShift first = candidates.get(0);
        WorkShift second = candidates.get(1);
        // 确认后：本班次确认、其余候选取代、任务已排班
        workShiftService.confirm(first.getId());
        assertEquals(ShiftStatus.CONFIRMED, workShiftService.mustGet(first.getId()).getStatus());
        assertEquals(ShiftStatus.SUPERSEDED, workShiftService.mustGet(second.getId()).getStatus());
        assertEquals(MaintenanceTaskStatus.SCHEDULED, maintenanceTaskService.mustGet(task.getId()).getStatus());

        // 幂等重试：重复确认同一班次返回原结果
        workShiftService.confirm(first.getId());
        // 唯一可用结果：确认另一候选被拒绝
        assertThrows(IllegalArgumentException.class, () -> workShiftService.confirm(second.getId()));
        // 已确认班次的任务不能重新生成候选
        MaintenanceTask finalTask = maintenanceTaskService.mustGet(task.getId());
        assertThrows(IllegalArgumentException.class,
                () -> maintenanceTaskService.generateShifts(finalTask.getId()));

        // 确认时资质过期：生成后吊销资质，确认被拦截（独立资质类型隔离人员池）
        Turbine turbine2 = turbine("T406");
        Worker expiring = worker("WK-10", "HIGH_ALTITUDE_B", BASE.plusDays(10));
        worker("WK-11", "HIGH_ALTITUDE_B", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine2, BASE, 4, 8.0, 0.0, LightningRisk.NONE));
        MaintenanceTaskCreateRequest req2 = new MaintenanceTaskCreateRequest();
        req2.setTaskNo("MT-406");
        req2.setTurbineId(turbine2.getId());
        req2.setRequiredQualification("HIGH_ALTITUDE_B");
        req2.setRequiredWorkers(2);
        req2.setEstimatedDurationHours(2);
        MaintenanceTask task2 = maintenanceTaskService.create(req2);
        WorkShift candidate2 = maintenanceTaskService.generateShifts(task2.getId()).get(0);
        expiring.setQualificationValidUntil(BASE.minusDays(1)); // 资质吊销/过期
        workerService.updateById(expiring);
        assertThrows(IllegalArgumentException.class, () -> workShiftService.confirm(candidate2.getId()));
        assertEquals(ShiftStatus.CANDIDATE, workShiftService.mustGet(candidate2.getId()).getStatus());
    }

    @Test
    void 窗口收窄_自动预警但不静默改期() {
        Turbine turbine = turbine("T407");
        worker("WK-12", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-13", "HIGH_ALTITUDE", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-407", 2, 2, null);
        WorkShift shift = maintenanceTaskService.generateShifts(task.getId()).get(0);
        workShiftService.confirm(shift.getId());

        // 补传：窗口收窄为 1 小时，班次 [08:00~10:00] 不再适窗
        weatherWindowService.report(windowReq(turbine, BASE, 1, 8.0, 0.0, LightningRisk.NONE));

        // 自动预警：值班调度收到窗口收窄通知
        ShiftDetailDto detail = workShiftService.detail(shift.getId());
        assertEquals(1, detail.getNotifications().size());
        ShiftNotification warning = detail.getNotifications().get(0);
        assertEquals(NotifyType.WINDOW_NARROWED, warning.getNotifyType());
        assertEquals(WeatherWindowService.DUTY_DISPATCHER, warning.getRecipient());
        assertNotNull(warning.getReceiptNo());
        assertTrue(warning.getContent().contains(shift.getShiftNo()));

        // 不静默改期：班次时刻保持不变
        WorkShift unchanged = workShiftService.mustGet(shift.getId());
        assertEquals(BASE, unchanged.getShiftStart());
        assertEquals(BASE.plusHours(2), unchanged.getShiftEnd());
        assertEquals(ShiftStatus.CONFIRMED, unchanged.getStatus());
        assertEquals(0, unchanged.getPostponeCount());
    }

    @Test
    void 窗口条件恶化_自动预警但不静默改期() {
        Turbine turbine = turbine("T411");
        worker("WK-20", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-21", "HIGH_ALTITUDE", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-411", 2, 2, null);
        WorkShift shift = maintenanceTaskService.generateShifts(task.getId()).get(0);
        workShiftService.confirm(shift.getId());

        // 补传：跨度不变但风速超限（条件恶化）
        weatherWindowService.report(windowReq(turbine, BASE, 4, 16.0, 0.0, LightningRisk.NONE));

        ShiftDetailDto detail = workShiftService.detail(shift.getId());
        assertEquals(1, detail.getNotifications().size());
        assertEquals(NotifyType.WINDOW_NARROWED, detail.getNotifications().get(0).getNotifyType());
        // 班次保持不变
        WorkShift unchanged = workShiftService.mustGet(shift.getId());
        assertEquals(BASE, unchanged.getShiftStart());
        assertEquals(ShiftStatus.CONFIRMED, unchanged.getStatus());
    }

    @Test
    void 作业前二次确认_未通过不能开工() {
        Turbine turbine = turbine("T408");
        worker("WK-14", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-15", "HIGH_ALTITUDE", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-408", 2, 2, null);
        WorkShift shift = maintenanceTaskService.generateShifts(task.getId()).get(0);
        workShiftService.confirm(shift.getId());

        // 未二次确认不能开工
        assertThrows(IllegalArgumentException.class, () -> workShiftService.start(shift.getId()));
        // 二次确认 FAIL（风速超限）不能开工
        WeatherConfirmation fail = workShiftService.confirmWeather(shift.getId(),
                weather(18.0, 0.0, LightningRisk.NONE));
        assertEquals(ConfirmationResult.FAIL, fail.getResult());
        assertThrows(IllegalArgumentException.class, () -> workShiftService.start(shift.getId()));
        // 二次确认 PASS 后开工，任务进入作业中
        WeatherConfirmation pass = workShiftService.confirmWeather(shift.getId(),
                weather(6.0, 0.0, LightningRisk.NONE));
        assertEquals(ConfirmationResult.PASS, pass.getResult());
        workShiftService.start(shift.getId());
        assertEquals(ShiftStatus.IN_PROGRESS, workShiftService.mustGet(shift.getId()).getStatus());
        assertEquals(MaintenanceTaskStatus.IN_PROGRESS, maintenanceTaskService.mustGet(task.getId()).getStatus());

        workShiftService.complete(shift.getId());
        assertEquals(ShiftStatus.COMPLETED, workShiftService.mustGet(shift.getId()).getStatus());
        assertEquals(MaintenanceTaskStatus.COMPLETED, maintenanceTaskService.mustGet(task.getId()).getStatus());
    }

    @Test
    void 延期_保留原计划与通知回执() {
        Turbine turbine = turbine("T409");
        worker("WK-16", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-17", "HIGH_ALTITUDE", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-409", 2, 2, null);
        WorkShift shift = maintenanceTaskService.generateShifts(task.getId()).get(0);
        workShiftService.confirm(shift.getId());

        // 第一次延期
        PostponeRequest first = new PostponeRequest();
        first.setNewStart(BASE.plusDays(1));
        first.setNewEnd(BASE.plusDays(1).plusHours(2));
        first.setReason("现场风速突增");
        first.setRecipient("作业班长");
        first.setPostponedBy("值班调度");
        workShiftService.postpone(shift.getId(), first);

        WorkShift postponed = workShiftService.mustGet(shift.getId());
        // 原计划固化
        assertEquals(BASE, postponed.getOriginalStart());
        assertEquals(BASE.plusHours(2), postponed.getOriginalEnd());
        assertEquals(BASE.plusDays(1), postponed.getShiftStart());
        assertEquals(1, postponed.getPostponeCount());

        // 第二次延期：原计划不变
        PostponeRequest second = new PostponeRequest();
        second.setNewStart(BASE.plusDays(2));
        second.setNewEnd(BASE.plusDays(2).plusHours(2));
        second.setReason("雷雨过境");
        second.setRecipient("作业班长");
        second.setPostponedBy("值班调度");
        workShiftService.postpone(shift.getId(), second);

        WorkShift twice = workShiftService.mustGet(shift.getId());
        assertEquals(BASE, twice.getOriginalStart());
        assertEquals(BASE.plusHours(2), twice.getOriginalEnd());
        assertEquals(BASE.plusDays(2), twice.getShiftStart());
        assertEquals(2, twice.getPostponeCount());

        // 延期记录与通知回执
        ShiftDetailDto detail = workShiftService.detail(shift.getId());
        List<ShiftPostpone> postpones = detail.getPostpones();
        assertEquals(2, postpones.size());
        assertEquals(BASE, postpones.get(0).getOldStart());
        assertEquals(BASE.plusDays(1), postpones.get(0).getNewStart());
        assertEquals(BASE.plusDays(1), postpones.get(1).getOldStart());
        assertEquals("雷雨过境", postpones.get(1).getReason());
        List<ShiftNotification> notifications = detail.getNotifications();
        assertEquals(2, notifications.size());
        assertEquals(NotifyType.POSTPONED, notifications.get(0).getNotifyType());
        assertEquals("作业班长", notifications.get(0).getRecipient());
        assertNotNull(notifications.get(0).getReceiptNo());
        // 延期记录关联通知回执
        assertEquals(notifications.get(0).getId(), postpones.get(0).getNotificationId());
        assertEquals(notifications.get(1).getId(), postpones.get(1).getNotificationId());
    }

    @Test
    void 取消确认班次_任务回到排班中可重新生成() {
        Turbine turbine = turbine("T410");
        worker("WK-18", "HIGH_ALTITUDE", BASE.plusDays(10));
        worker("WK-19", "HIGH_ALTITUDE", BASE.plusDays(10));
        weatherWindowService.report(windowReq(turbine, BASE, 4, 8.0, 0.0, LightningRisk.NONE));

        MaintenanceTask task = task(turbine, "MT-410", 2, 2, null);
        WorkShift shift = maintenanceTaskService.generateShifts(task.getId()).get(0);
        workShiftService.confirm(shift.getId());
        workShiftService.cancel(shift.getId());

        assertEquals(ShiftStatus.CANCELLED, workShiftService.mustGet(shift.getId()).getStatus());
        assertEquals(MaintenanceTaskStatus.PLANNING, maintenanceTaskService.mustGet(task.getId()).getStatus());
        // 重新生成：取消的班次不阻塞，候选重建
        assertEquals(1, maintenanceTaskService.generateShifts(task.getId()).size());
        assertNull(maintenanceTaskService.detail(task.getId()).getShifts().stream()
                .filter(s -> s.getStatus() == ShiftStatus.CANCELLED).findFirst().orElse(null));
    }
}
