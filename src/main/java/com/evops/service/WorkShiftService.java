package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.ConfirmWeatherRequest;
import com.evops.dto.PostponeRequest;
import com.evops.dto.ShiftDetailDto;
import com.evops.entity.MaintenanceTask;
import com.evops.entity.ShiftAssignment;
import com.evops.entity.ShiftNotification;
import com.evops.entity.ShiftPostpone;
import com.evops.entity.WeatherConfirmation;
import com.evops.entity.WorkShift;
import com.evops.entity.Worker;
import com.evops.enums.AssignType;
import com.evops.enums.ConfirmationResult;
import com.evops.enums.MaintenanceTaskStatus;
import com.evops.enums.NotifyType;
import com.evops.enums.ShiftStatus;
import com.evops.enums.WorkerStatus;
import com.evops.mapper.MaintenanceTaskMapper;
import com.evops.mapper.ShiftAssignmentMapper;
import com.evops.mapper.ShiftPostponeMapper;
import com.evops.mapper.ShiftNotificationMapper;
import com.evops.mapper.WeatherConfirmationMapper;
import com.evops.mapper.WorkShiftMapper;
import com.evops.mapper.WorkerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 班次：确认、作业前二次确认、开工、完工、延期、取消。
 *
 * 口径：
 * - 每个任务至多一个确认班次（唯一可用排班结果）；确认幂等，重试返回原结果；
 * - 确认时复核人员资质，过期资质不能被排入任务；
 * - 开工前必须二次确认天气且最近一次为 PASS；
 * - 延期保留原计划（首次延期固化 original_start/end）与通知回执，系统不静默改期。
 */
@Service
public class WorkShiftService extends ServiceImpl<WorkShiftMapper, WorkShift> {

    private final MaintenanceTaskMapper taskMapper;
    private final ShiftAssignmentMapper assignmentMapper;
    private final WeatherConfirmationMapper confirmationMapper;
    private final ShiftPostponeMapper postponeMapper;
    private final ShiftNotificationMapper notificationMapper;
    private final WorkerMapper workerMapper;
    private final ShiftNotificationService notificationService;
    private final AuditService auditService;

    public WorkShiftService(MaintenanceTaskMapper taskMapper, ShiftAssignmentMapper assignmentMapper,
                            WeatherConfirmationMapper confirmationMapper, ShiftPostponeMapper postponeMapper,
                            ShiftNotificationMapper notificationMapper, WorkerMapper workerMapper,
                            ShiftNotificationService notificationService, AuditService auditService) {
        this.taskMapper = taskMapper;
        this.assignmentMapper = assignmentMapper;
        this.confirmationMapper = confirmationMapper;
        this.postponeMapper = postponeMapper;
        this.notificationMapper = notificationMapper;
        this.workerMapper = workerMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * 确认班次：本班次确认、其余候选取代，任务进入已排班。
     * 幂等：重复确认同一班次返回原结果；任务已有其他确认班次时拒绝。
     */
    @Transactional
    public WorkShift confirm(Long shiftId) {
        WorkShift shift = mustGet(shiftId);
        if (shift.getStatus() == ShiftStatus.CONFIRMED) {
            return shift; // 幂等重试：已确认直接返回
        }
        if (shift.getStatus() != ShiftStatus.CANDIDATE) {
            throw new IllegalArgumentException("班次状态不允许确认: " + shift.getStatus());
        }
        MaintenanceTask task = mustGetTask(shift.getTaskId());
        // 唯一可用结果：任务已有确认班次时拒绝再确认
        Long confirmedShifts = getBaseMapper().selectCount(new QueryWrapper<WorkShift>()
                .eq("task_id", task.getId())
                .eq("status", ShiftStatus.CONFIRMED.name()));
        if (confirmedShifts != null && confirmedShifts > 0) {
            throw new IllegalArgumentException("任务已有确认班次，唯一可用结果不可重复: " + task.getTaskNo());
        }
        // 过期资质不能被排入：确认时复核全部已分配人员
        List<ShiftAssignment> workerAssignments = assignmentMapper.selectList(
                new QueryWrapper<ShiftAssignment>()
                        .eq("shift_id", shift.getId())
                        .eq("assign_type", AssignType.WORKER.name()));
        for (ShiftAssignment assignment : workerAssignments) {
            Worker worker = workerMapper.selectById(assignment.getWorkerId());
            if (worker == null || worker.getStatus() != WorkerStatus.ACTIVE
                    || worker.getQualificationValidUntil().isBefore(shift.getShiftEnd())) {
                throw new IllegalArgumentException("人员资质已过期或离职，不能排入任务: "
                        + (worker != null ? worker.getWorkerNo() : assignment.getWorkerId()));
            }
        }

        shift.setStatus(ShiftStatus.CONFIRMED);
        updateById(shift);
        List<WorkShift> others = getBaseMapper().selectList(new QueryWrapper<WorkShift>()
                .eq("task_id", task.getId())
                .eq("status", ShiftStatus.CANDIDATE.name())
                .ne("id", shift.getId()));
        for (WorkShift other : others) {
            other.setStatus(ShiftStatus.SUPERSEDED);
            getBaseMapper().updateById(other);
        }
        task.setStatus(MaintenanceTaskStatus.SCHEDULED);
        taskMapper.updateById(task);
        auditService.record("WORK_SHIFT", shift.getId(), "SHIFT_CONFIRM",
                "{\"shiftNo\":\"" + shift.getShiftNo() + "\"}", null);
        return shift;
    }

    /** 作业前二次确认天气：每次确认留痕，结果 PASS/FAIL */
    @Transactional
    public WeatherConfirmation confirmWeather(Long shiftId, ConfirmWeatherRequest req) {
        WorkShift shift = mustGet(shiftId);
        if (shift.getStatus() != ShiftStatus.CONFIRMED) {
            throw new IllegalArgumentException("仅已确认班次需要作业前二次确认: " + shift.getStatus());
        }
        ConfirmationResult result = WeatherWindowService.suitableConditions(
                req.getWindSpeed(), req.getRainfallMm(), req.getLightningRisk())
                ? ConfirmationResult.PASS : ConfirmationResult.FAIL;
        WeatherConfirmation confirmation = new WeatherConfirmation();
        confirmation.setShiftId(shift.getId());
        confirmation.setWindSpeed(req.getWindSpeed());
        confirmation.setRainfallMm(req.getRainfallMm());
        confirmation.setLightningRisk(req.getLightningRisk());
        confirmation.setResult(result);
        confirmation.setConfirmedBy(req.getConfirmedBy());
        confirmation.setConfirmedAt(LocalDateTime.now());
        confirmationMapper.insert(confirmation);
        auditService.record("WORK_SHIFT", shift.getId(), "WEATHER_CONFIRM",
                result.name() + " by " + req.getConfirmedBy(), req.getConfirmedBy());
        return confirmation;
    }

    /** 开工：须最近一次二次确认为 PASS */
    @Transactional
    public WorkShift start(Long shiftId) {
        WorkShift shift = mustGet(shiftId);
        if (shift.getStatus() == ShiftStatus.IN_PROGRESS) {
            return shift; // 幂等重试
        }
        if (shift.getStatus() != ShiftStatus.CONFIRMED) {
            throw new IllegalArgumentException("班次状态不允许开工: " + shift.getStatus());
        }
        WeatherConfirmation latest = confirmationMapper.selectOne(new QueryWrapper<WeatherConfirmation>()
                .eq("shift_id", shift.getId())
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (latest == null || latest.getResult() != ConfirmationResult.PASS) {
            throw new IllegalArgumentException("作业前二次确认未通过，不能开工");
        }
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        updateById(shift);
        MaintenanceTask task = mustGetTask(shift.getTaskId());
        task.setStatus(MaintenanceTaskStatus.IN_PROGRESS);
        taskMapper.updateById(task);
        auditService.record("WORK_SHIFT", shift.getId(), "SHIFT_START", null, null);
        return shift;
    }

    /** 完工 */
    @Transactional
    public WorkShift complete(Long shiftId) {
        WorkShift shift = mustGet(shiftId);
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("班次状态不允许完工: " + shift.getStatus());
        }
        shift.setStatus(ShiftStatus.COMPLETED);
        updateById(shift);
        MaintenanceTask task = mustGetTask(shift.getTaskId());
        task.setStatus(MaintenanceTaskStatus.COMPLETED);
        taskMapper.updateById(task);
        auditService.record("WORK_SHIFT", shift.getId(), "SHIFT_COMPLETE", null, null);
        return shift;
    }

    /**
     * 延期：显式改期（区别于静默改期）。
     * 首次延期把当前计划固化为原计划（之后不再改变）；
     * 每次延期留存延期记录与通知回执。
     */
    @Transactional
    public WorkShift postpone(Long shiftId, PostponeRequest req) {
        WorkShift shift = mustGet(shiftId);
        if (shift.getStatus() != ShiftStatus.CONFIRMED) {
            throw new IllegalArgumentException("仅已确认班次可延期: " + shift.getStatus());
        }
        if (!req.getNewEnd().isAfter(req.getNewStart())) {
            throw new IllegalArgumentException("新结束时刻必须晚于新开始时刻");
        }
        // 通知回执
        ShiftNotification notification = notificationService.create(shift.getId(), shift.getTaskId(),
                NotifyType.POSTPONED, req.getRecipient(),
                "班次 " + shift.getShiftNo() + " 延期：[" + shift.getShiftStart() + "~" + shift.getShiftEnd()
                        + "] → [" + req.getNewStart() + "~" + req.getNewEnd() + "]，原因：" + req.getReason());
        // 延期记录：保留新旧计划
        ShiftPostpone postpone = new ShiftPostpone();
        postpone.setShiftId(shift.getId());
        postpone.setOldStart(shift.getShiftStart());
        postpone.setOldEnd(shift.getShiftEnd());
        postpone.setNewStart(req.getNewStart());
        postpone.setNewEnd(req.getNewEnd());
        postpone.setReason(req.getReason());
        postpone.setNotificationId(notification.getId());
        postpone.setPostponedBy(req.getPostponedBy());
        postpone.setPostponedAt(LocalDateTime.now());
        postponeMapper.insert(postpone);
        // 首次延期固化原计划
        if (shift.getOriginalStart() == null) {
            shift.setOriginalStart(shift.getShiftStart());
            shift.setOriginalEnd(shift.getShiftEnd());
        }
        shift.setShiftStart(req.getNewStart());
        shift.setShiftEnd(req.getNewEnd());
        shift.setPostponeCount(shift.getPostponeCount() + 1);
        updateById(shift);
        auditService.record("WORK_SHIFT", shift.getId(), "SHIFT_POSTPONE",
                "第 " + shift.getPostponeCount() + " 次延期，回执 " + notification.getReceiptNo(), req.getPostponedBy());
        return shift;
    }

    /** 取消：候选直接取消；已确认取消后任务回到排班中，可重新生成候选 */
    @Transactional
    public WorkShift cancel(Long shiftId) {
        WorkShift shift = mustGet(shiftId);
        if (shift.getStatus() != ShiftStatus.CANDIDATE && shift.getStatus() != ShiftStatus.CONFIRMED) {
            throw new IllegalArgumentException("班次状态不允许取消: " + shift.getStatus());
        }
        boolean wasConfirmed = shift.getStatus() == ShiftStatus.CONFIRMED;
        shift.setStatus(ShiftStatus.CANCELLED);
        updateById(shift);
        if (wasConfirmed) {
            MaintenanceTask task = mustGetTask(shift.getTaskId());
            task.setStatus(MaintenanceTaskStatus.PLANNING);
            taskMapper.updateById(task);
        }
        auditService.record("WORK_SHIFT", shift.getId(), "SHIFT_CANCEL", null, null);
        return shift;
    }

    /** 班次详情：班次 + 人员装备分配 + 二次确认 + 延期记录 + 通知回执 */
    public ShiftDetailDto detail(Long shiftId) {
        WorkShift shift = mustGet(shiftId);
        ShiftDetailDto dto = new ShiftDetailDto();
        dto.setShift(shift);
        dto.setAssignments(assignmentMapper.selectList(new QueryWrapper<ShiftAssignment>()
                .eq("shift_id", shiftId).orderByAsc("id")));
        dto.setConfirmations(confirmationMapper.selectList(new QueryWrapper<WeatherConfirmation>()
                .eq("shift_id", shiftId).orderByAsc("id")));
        dto.setPostpones(postponeMapper.selectList(new QueryWrapper<ShiftPostpone>()
                .eq("shift_id", shiftId).orderByAsc("id")));
        dto.setNotifications(notificationMapper.selectList(new QueryWrapper<ShiftNotification>()
                .eq("shift_id", shiftId).orderByAsc("id")));
        return dto;
    }

    public WorkShift mustGet(Long id) {
        WorkShift shift = getById(id);
        if (shift == null) {
            throw new IllegalArgumentException("班次不存在: " + id);
        }
        return shift;
    }

    private MaintenanceTask mustGetTask(Long taskId) {
        MaintenanceTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("高空作业任务不存在: " + taskId);
        }
        return task;
    }
}
