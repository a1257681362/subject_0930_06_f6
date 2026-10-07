package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.MaintenanceTaskCreateRequest;
import com.evops.dto.TaskDetailDto;
import com.evops.entity.Equipment;
import com.evops.entity.MaintenanceTask;
import com.evops.entity.ShiftAssignment;
import com.evops.entity.WeatherWindow;
import com.evops.entity.WorkShift;
import com.evops.entity.Worker;
import com.evops.enums.AssignType;
import com.evops.enums.EquipmentStatus;
import com.evops.enums.MaintenanceTaskStatus;
import com.evops.enums.ShiftStatus;
import com.evops.enums.WorkerStatus;
import com.evops.mapper.DefectMapper;
import com.evops.mapper.MaintenanceTaskMapper;
import com.evops.mapper.ShiftAssignmentMapper;
import com.evops.mapper.WorkShiftMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 高空作业任务：结合天气窗口、人员资质、装备可用性与维修时长生成候选班次。
 *
 * 口径：
 * - 候选班次 = 适窗窗口起点起、维修时长的一段，须落在窗口内；
 * - 人员须在职、资质类型匹配且资质有效期覆盖整个班次（过期资质不能被排入）；
 * - 装备须可用且可用窗口覆盖整个班次；
 * - 生成幂等：重试/补录触发重新生成时，先清除上一轮落选班次再重建，
 *   同一任务同一窗口至多一条班次；已确认班次的任务不允许重新生成。
 */
@Service
public class MaintenanceTaskService extends ServiceImpl<MaintenanceTaskMapper, MaintenanceTask> {

    private final TurbineService turbineService;
    private final DefectMapper defectMapper;
    private final WeatherWindowService weatherWindowService;
    private final WorkerService workerService;
    private final EquipmentService equipmentService;
    private final WorkShiftMapper workShiftMapper;
    private final ShiftAssignmentMapper assignmentMapper;
    private final AuditService auditService;

    public MaintenanceTaskService(TurbineService turbineService, DefectMapper defectMapper,
                                  WeatherWindowService weatherWindowService, WorkerService workerService,
                                  EquipmentService equipmentService, WorkShiftMapper workShiftMapper,
                                  ShiftAssignmentMapper assignmentMapper, AuditService auditService) {
        this.turbineService = turbineService;
        this.defectMapper = defectMapper;
        this.weatherWindowService = weatherWindowService;
        this.workerService = workerService;
        this.equipmentService = equipmentService;
        this.workShiftMapper = workShiftMapper;
        this.assignmentMapper = assignmentMapper;
        this.auditService = auditService;
    }

    /** 创建高空作业任务 */
    public MaintenanceTask create(MaintenanceTaskCreateRequest req) {
        turbineService.mustGet(req.getTurbineId());
        if (req.getDefectId() != null && defectMapper.selectById(req.getDefectId()) == null) {
            throw new IllegalArgumentException("缺陷不存在: " + req.getDefectId());
        }
        if (req.getEstimatedDurationHours() <= 0) {
            throw new IllegalArgumentException("维修时长必须为正数");
        }
        String taskNo = StringUtils.hasText(req.getTaskNo())
                ? req.getTaskNo()
                : "MT" + System.currentTimeMillis();
        Long dup = count(new QueryWrapper<MaintenanceTask>().eq("task_no", taskNo));
        if (dup > 0) {
            throw new IllegalArgumentException("任务编号已存在: " + taskNo);
        }
        MaintenanceTask task = new MaintenanceTask();
        task.setTaskNo(taskNo);
        task.setTurbineId(req.getTurbineId());
        task.setDefectId(req.getDefectId());
        task.setRequiredQualification(req.getRequiredQualification());
        task.setRequiredWorkers(req.getRequiredWorkers() != null ? req.getRequiredWorkers() : 2);
        task.setRequiredEquipmentType(req.getRequiredEquipmentType());
        task.setEstimatedDurationHours(req.getEstimatedDurationHours());
        task.setStatus(MaintenanceTaskStatus.PLANNING);
        save(task);
        auditService.record("MAINTENANCE_TASK", task.getId(), "TASK_CREATE",
                "{\"taskNo\":\"" + taskNo + "\"}", null);
        return task;
    }

    /**
     * 生成候选班次（幂等）：重试/补录后重新生成时清除上一轮落选班次再重建，
     * 不会堆积重复候选；任务已有确认班次时拒绝重新生成。
     */
    @Transactional
    public List<WorkShift> generateShifts(Long taskId) {
        MaintenanceTask task = mustGet(taskId);
        if (task.getStatus() != MaintenanceTaskStatus.PLANNING) {
            throw new IllegalArgumentException("任务已有确认班次，不能重新生成候选班次: " + task.getStatus());
        }
        clearUnelectedShifts(taskId);

        List<WorkShift> created = new ArrayList<>();
        List<WeatherWindow> windows = weatherWindowService.listByTurbine(task.getTurbineId());
        for (WeatherWindow window : windows) {
            if (!WeatherWindowService.isSuitable(window)) {
                continue;
            }
            LocalDateTime start = window.getWindowStart();
            LocalDateTime end = start.plusHours(task.getEstimatedDurationHours());
            if (end.isAfter(window.getWindowEnd())) {
                continue; // 维修时长超出窗口，排不下
            }
            List<Worker> workers = pickQualifiedWorkers(task, end);
            if (workers.size() < task.getRequiredWorkers()) {
                continue; // 资质有效人数不足
            }
            Equipment equipment = null;
            if (StringUtils.hasText(task.getRequiredEquipmentType())) {
                equipment = pickAvailableEquipment(task.getRequiredEquipmentType(), start, end);
                if (equipment == null) {
                    continue; // 无可用装备
                }
            }
            created.add(createCandidate(task, window, start, end, workers, equipment));
        }
        auditService.record("MAINTENANCE_TASK", taskId, "SHIFTS_GENERATE",
                "候选班次 " + created.size() + " 个", null);
        return created;
    }

    /** 清除上一轮落选班次（候选/被取代/已取消）及其分配；确认与作业中的班次不受影响 */
    private void clearUnelectedShifts(Long taskId) {
        List<WorkShift> old = workShiftMapper.selectList(new QueryWrapper<WorkShift>()
                .eq("task_id", taskId)
                .in("status", Arrays.asList(
                        ShiftStatus.CANDIDATE.name(), ShiftStatus.SUPERSEDED.name(), ShiftStatus.CANCELLED.name())));
        for (WorkShift shift : old) {
            assignmentMapper.delete(new QueryWrapper<ShiftAssignment>().eq("shift_id", shift.getId()));
            workShiftMapper.deleteById(shift.getId());
        }
    }

    /** 资质有效人数：在职 + 资质类型匹配 + 有效期覆盖班次结束时刻，按有效期近者优先 */
    private List<Worker> pickQualifiedWorkers(MaintenanceTask task, LocalDateTime shiftEnd) {
        List<Worker> matched = workerService.list(new QueryWrapper<Worker>()
                .eq("status", WorkerStatus.ACTIVE.name())
                .eq("qualification_type", task.getRequiredQualification())
                .ge("qualification_valid_until", shiftEnd)
                .orderByAsc("qualification_valid_until")
                .orderByAsc("id"));
        return matched.stream().limit(task.getRequiredWorkers()).collect(Collectors.toList());
    }

    /** 可用装备：可用状态 + 可用窗口覆盖整个班次 */
    private Equipment pickAvailableEquipment(String equipmentType, LocalDateTime start, LocalDateTime end) {
        List<Equipment> matched = equipmentService.list(new QueryWrapper<Equipment>()
                .eq("equipment_type", equipmentType)
                .eq("status", EquipmentStatus.AVAILABLE.name())
                .orderByAsc("id"));
        return matched.stream()
                .filter(e -> (e.getAvailableFrom() == null || !e.getAvailableFrom().isAfter(start))
                        && (e.getAvailableTo() == null || !e.getAvailableTo().isBefore(end)))
                .findFirst()
                .orElse(null);
    }

    private WorkShift createCandidate(MaintenanceTask task, WeatherWindow window,
                                      LocalDateTime start, LocalDateTime end,
                                      List<Worker> workers, Equipment equipment) {
        WorkShift shift = new WorkShift();
        shift.setShiftNo("SH" + System.currentTimeMillis() + "-" + window.getId());
        shift.setTaskId(task.getId());
        shift.setWindowId(window.getId());
        shift.setShiftStart(start);
        shift.setShiftEnd(end);
        shift.setPostponeCount(0);
        shift.setStatus(ShiftStatus.CANDIDATE);
        workShiftMapper.insert(shift);
        for (Worker worker : workers) {
            ShiftAssignment assignment = new ShiftAssignment();
            assignment.setShiftId(shift.getId());
            assignment.setAssignType(AssignType.WORKER);
            assignment.setWorkerId(worker.getId());
            assignmentMapper.insert(assignment);
        }
        if (equipment != null) {
            ShiftAssignment assignment = new ShiftAssignment();
            assignment.setShiftId(shift.getId());
            assignment.setAssignType(AssignType.EQUIPMENT);
            assignment.setEquipmentId(equipment.getId());
            assignmentMapper.insert(assignment);
        }
        return shift;
    }

    /** 任务详情：任务 + 全部班次 */
    public TaskDetailDto detail(Long taskId) {
        TaskDetailDto dto = new TaskDetailDto();
        dto.setTask(mustGet(taskId));
        dto.setShifts(workShiftMapper.selectList(new QueryWrapper<WorkShift>()
                .eq("task_id", taskId)
                .orderByAsc("id")));
        return dto;
    }

    public MaintenanceTask mustGet(Long id) {
        MaintenanceTask task = getById(id);
        if (task == null) {
            throw new IllegalArgumentException("高空作业任务不存在: " + id);
        }
        return task;
    }
}
