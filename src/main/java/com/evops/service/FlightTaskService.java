package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.FlightTaskCreateRequest;
import com.evops.entity.FlightRoute;
import com.evops.entity.FlightTask;
import com.evops.enums.TaskStatus;
import com.evops.mapper.FlightTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 飞行任务：创建时绑定航线当前发布版本。
 * 未起飞任务随新版本改绑（见 RouteService#doPublish）；起飞后版本锁定。
 */
@Service
public class FlightTaskService extends ServiceImpl<FlightTaskMapper, FlightTask> {

    private final RouteService routeService;
    private final AuditService auditService;

    public FlightTaskService(RouteService routeService, AuditService auditService) {
        this.routeService = routeService;
        this.auditService = auditService;
    }

    /** 创建飞行任务：绑定航线当前发布版本 */
    public FlightTask create(FlightTaskCreateRequest req) {
        FlightRoute route = routeService.mustGet(req.getRouteId());
        if (route.getCurrentVersionId() == null) {
            throw new IllegalArgumentException("航线尚未发布版本，不能创建飞行任务: " + route.getId());
        }
        String taskNo = StringUtils.hasText(req.getTaskNo())
                ? req.getTaskNo()
                : "FT" + System.currentTimeMillis();
        Long dup = count(new QueryWrapper<FlightTask>().eq("task_no", taskNo));
        if (dup > 0) {
            throw new IllegalArgumentException("任务编号已存在: " + taskNo);
        }

        FlightTask task = new FlightTask();
        task.setTaskNo(taskNo);
        task.setRouteId(route.getId());
        task.setRouteVersionId(route.getCurrentVersionId());
        task.setTurbineId(route.getTurbineId());
        task.setBladePosition(route.getBladePosition());
        task.setPlannedTakeoffAt(req.getPlannedTakeoffAt());
        task.setStatus(TaskStatus.PLANNED);
        save(task);

        auditService.record("TASK", task.getId(), "TASK_CREATE",
                "{\"routeVersionId\":" + task.getRouteVersionId() + "}", null);
        return task;
    }

    /** 起飞：版本自此锁定，后续新版本不再作用于该任务 */
    public FlightTask takeoff(Long id) {
        FlightTask task = mustGet(id);
        if (task.getStatus() != TaskStatus.PLANNED) {
            throw new IllegalArgumentException("任务状态不允许起飞: " + task.getStatus());
        }
        task.setStatus(TaskStatus.TAKEN_OFF);
        task.setTakeoffAt(LocalDateTime.now());
        updateById(task);
        auditService.record("TASK", task.getId(), "TASK_TAKEOFF",
                "{\"routeVersionId\":" + task.getRouteVersionId() + "}", null);
        return task;
    }

    /** 完成 */
    public FlightTask complete(Long id) {
        FlightTask task = mustGet(id);
        if (task.getStatus() != TaskStatus.TAKEN_OFF) {
            throw new IllegalArgumentException("任务状态不允许完成: " + task.getStatus());
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        updateById(task);
        auditService.record("TASK", task.getId(), "TASK_COMPLETE", null, null);
        return task;
    }

    /** 取消（仅未起飞） */
    public FlightTask cancel(Long id) {
        FlightTask task = mustGet(id);
        if (task.getStatus() != TaskStatus.PLANNED) {
            throw new IllegalArgumentException("任务状态不允许取消: " + task.getStatus());
        }
        task.setStatus(TaskStatus.CANCELLED);
        updateById(task);
        auditService.record("TASK", task.getId(), "TASK_CANCEL", null, null);
        return task;
    }

    public FlightTask mustGet(Long id) {
        FlightTask task = getById(id);
        if (task == null) {
            throw new IllegalArgumentException("飞行任务不存在: " + id);
        }
        return task;
    }
}
