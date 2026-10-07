package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.common.GeoUtil;
import com.evops.common.RouteCheckException;
import com.evops.dto.CheckResultDto;
import com.evops.dto.ConflictDto;
import com.evops.dto.PublishCheckRequest;
import com.evops.dto.RouteCreateRequest;
import com.evops.dto.RouteVersionRequest;
import com.evops.dto.WaypointDto;
import com.evops.entity.FlightRoute;
import com.evops.entity.FlightRouteVersion;
import com.evops.entity.FlightTask;
import com.evops.enums.TaskStatus;
import com.evops.enums.VersionStatus;
import com.evops.mapper.FlightRouteMapper;
import com.evops.mapper.FlightRouteVersionMapper;
import com.evops.mapper.FlightTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检航线：按 风机+叶片方位+拍摄距离 配置，内容版本化。
 * 发布前校验（禁飞区/风速/电量）通过才生效；新版本发布只改绑未起飞任务。
 */
@Service
public class RouteService extends ServiceImpl<FlightRouteMapper, FlightRoute> {

    private final FlightRouteVersionMapper versionMapper;
    private final FlightTaskMapper taskMapper;
    private final TurbineService turbineService;
    private final RouteCheckService routeCheckService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RouteService(FlightRouteVersionMapper versionMapper, FlightTaskMapper taskMapper,
                        TurbineService turbineService, RouteCheckService routeCheckService,
                        AuditService auditService, ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.turbineService = turbineService;
        this.routeCheckService = routeCheckService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** 新建航线（含首版本草稿）。风机+方位+拍摄距离 唯一 */
    @Transactional
    public FlightRoute create(RouteCreateRequest req) {
        turbineService.mustGet(req.getTurbineId());
        Long dimDup = count(new QueryWrapper<FlightRoute>()
                .eq("turbine_id", req.getTurbineId())
                .eq("blade_position", req.getBladePosition())
                .eq("shoot_distance_m", req.getShootDistanceM()));
        if (dimDup > 0) {
            throw new IllegalArgumentException("该风机+方位+拍摄距离已配置航线: "
                    + req.getBladePosition() + " / " + req.getShootDistanceM() + "m");
        }
        String routeNo = StringUtils.hasText(req.getRouteNo())
                ? req.getRouteNo()
                : "RT" + System.currentTimeMillis();
        Long noDup = count(new QueryWrapper<FlightRoute>().eq("route_no", routeNo));
        if (noDup > 0) {
            throw new IllegalArgumentException("航线编号已存在: " + routeNo);
        }

        FlightRoute route = new FlightRoute();
        route.setRouteNo(routeNo);
        route.setTurbineId(req.getTurbineId());
        route.setBladePosition(req.getBladePosition());
        route.setShootDistanceM(req.getShootDistanceM());
        route.setName(req.getName());
        save(route);

        createVersion(route.getId(), req.getWaypoints(),
                req.getRequiredBatteryPct(), req.getMaxWindSpeed());
        auditService.record("ROUTE", route.getId(), "ROUTE_CREATE",
                "{\"routeNo\":\"" + routeNo + "\"}", null);
        return route;
    }

    /** 新增版本草稿：版本号航线内递增 */
    public FlightRouteVersion addVersion(Long routeId, RouteVersionRequest req) {
        mustGet(routeId);
        return createVersion(routeId, req.getWaypoints(),
                req.getRequiredBatteryPct(), req.getMaxWindSpeed());
    }

    FlightRouteVersion createVersion(Long routeId, List<WaypointDto> waypoints,
                                     Integer requiredBatteryPct, Double maxWindSpeed) {
        normalizeAndValidate(waypoints, requiredBatteryPct, maxWindSpeed);
        FlightRouteVersion latest = versionMapper.selectOne(new QueryWrapper<FlightRouteVersion>()
                .eq("route_id", routeId)
                .orderByDesc("version_no")
                .last("limit 1"));

        FlightRouteVersion version = new FlightRouteVersion();
        version.setRouteId(routeId);
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setWaypointsJson(writeJson(waypoints));
        version.setWaypointCount(waypoints.size());
        version.setEstimatedDistanceM(GeoUtil.totalDistanceM(waypoints));
        version.setRequiredBatteryPct(requiredBatteryPct);
        version.setMaxWindSpeed(maxWindSpeed);
        version.setStatus(VersionStatus.DRAFT);
        versionMapper.insert(version);

        auditService.record("VERSION", version.getId(), "VERSION_CREATE",
                "{\"routeId\":" + routeId + ",\"versionNo\":" + version.getVersionNo() + "}", null);
        return version;
    }

    /** 发布前校验（试算）：不推进状态，校验结果留痕在版本上 */
    @Transactional
    public CheckResultDto check(Long versionId, PublishCheckRequest req) {
        FlightRouteVersion version = mustGetVersion(versionId);
        List<ConflictDto> conflicts = routeCheckService.check(parseWaypoints(version),
                version.getMaxWindSpeed(), version.getRequiredBatteryPct(),
                req.getWindSpeed(), req.getBatteryPct());
        version.setCheckResultJson(checkResultJson(req.getWindSpeed(), req.getBatteryPct(), conflicts));
        versionMapper.updateById(version);

        CheckResultDto result = new CheckResultDto();
        result.setPass(conflicts.isEmpty());
        result.setConflicts(conflicts);
        return result;
    }

    /**
     * 发布版本：发布前校验（禁飞区/风速/电量）通过才生效。
     * 生效后旧版本退役，未起飞任务改绑新版本；已起飞任务保持原版本。
     */
    @Transactional
    public FlightRouteVersion publish(Long versionId, PublishCheckRequest req) {
        FlightRouteVersion version = mustGetVersion(versionId);
        FlightRoute route = mustGet(version.getRouteId());
        List<ConflictDto> conflicts = routeCheckService.check(parseWaypoints(version),
                version.getMaxWindSpeed(), version.getRequiredBatteryPct(),
                req.getWindSpeed(), req.getBatteryPct());
        if (!conflicts.isEmpty()) {
            throw new RouteCheckException(conflicts);
        }
        doPublish(route, version, req.getWindSpeed(), req.getBatteryPct());
        return version;
    }

    /**
     * 发布生效（改线审批终审也走这里，调用前须已完成发布前校验）：
     * 旧版本退役、新版本上架、未起飞任务改绑。
     */
    public void doPublish(FlightRoute route, FlightRouteVersion version,
                          Double windSpeed, Integer batteryPct) {
        if (!version.getRouteId().equals(route.getId())) {
            throw new IllegalArgumentException("版本不属于该航线: " + version.getId());
        }
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalArgumentException("仅草稿版本可发布: " + version.getStatus());
        }
        // 旧版本退役
        if (route.getCurrentVersionId() != null) {
            FlightRouteVersion current = versionMapper.selectById(route.getCurrentVersionId());
            if (current != null && current.getStatus() == VersionStatus.PUBLISHED) {
                current.setStatus(VersionStatus.RETIRED);
                versionMapper.updateById(current);
            }
        }
        version.setStatus(VersionStatus.PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());
        version.setCheckResultJson(checkResultJson(windSpeed, batteryPct, new ArrayList<>()));
        versionMapper.updateById(version);

        route.setCurrentVersionId(version.getId());
        updateById(route);

        // 未起飞任务改绑新版本；已起飞/已结案任务保持原版本
        List<FlightTask> planned = taskMapper.selectList(new QueryWrapper<FlightTask>()
                .eq("route_id", route.getId())
                .eq("status", TaskStatus.PLANNED)
                .ne("route_version_id", version.getId()));
        List<Long> reboundTaskIds = new ArrayList<>();
        for (FlightTask task : planned) {
            task.setRouteVersionId(version.getId());
            taskMapper.updateById(task);
            reboundTaskIds.add(task.getId());
            auditService.record("TASK", task.getId(), "TASK_REBIND",
                    "{\"routeVersionId\":" + version.getId() + "}", null);
        }
        auditService.record("ROUTE", route.getId(), "VERSION_PUBLISH",
                "{\"versionNo\":" + version.getVersionNo()
                        + ",\"reboundTaskIds\":" + reboundTaskIds + "}", null);
    }

    public FlightRoute mustGet(Long id) {
        FlightRoute route = getById(id);
        if (route == null) {
            throw new IllegalArgumentException("航线不存在: " + id);
        }
        return route;
    }

    public FlightRouteVersion mustGetVersion(Long id) {
        FlightRouteVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("航线版本不存在: " + id);
        }
        return version;
    }

    /** 版本详情（含解析后的航点串），历史版本同样可查，支撑追溯 */
    public FlightRouteVersion versionDetail(Long versionId) {
        FlightRouteVersion version = mustGetVersion(versionId);
        version.setWaypoints(parseWaypoints(version));
        return version;
    }

    public List<FlightRouteVersion> listVersions(Long routeId) {
        return versionMapper.selectList(new QueryWrapper<FlightRouteVersion>()
                .eq("route_id", routeId)
                .orderByAsc("version_no"));
    }

    public List<FlightRoute> listByTurbine(Long turbineId) {
        return list(new QueryWrapper<FlightRoute>()
                .eq("turbine_id", turbineId)
                .orderByAsc("id"));
    }

    public List<WaypointDto> parseWaypoints(FlightRouteVersion version) {
        try {
            return objectMapper.readValue(version.getWaypointsJson(),
                    new TypeReference<List<WaypointDto>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("航点串解析失败: " + version.getId(), e);
        }
    }

    private void normalizeAndValidate(List<WaypointDto> waypoints,
                                      Integer requiredBatteryPct, Double maxWindSpeed) {
        for (int i = 0; i < waypoints.size(); i++) {
            WaypointDto wp = waypoints.get(i);
            if (wp.getSeq() == null) {
                wp.setSeq(i + 1);
            }
            if (wp.getLat() == null || wp.getLng() == null
                    || Math.abs(wp.getLat()) > 90 || Math.abs(wp.getLng()) > 180) {
                throw new IllegalArgumentException("航点坐标非法: 序号 " + wp.getSeq());
            }
        }
        if (requiredBatteryPct != null && (requiredBatteryPct < 0 || requiredBatteryPct > 100)) {
            throw new IllegalArgumentException("预计耗电需在 0-100 之间: " + requiredBatteryPct);
        }
        if (maxWindSpeed != null && maxWindSpeed < 0) {
            throw new IllegalArgumentException("最大作业风速不能为负: " + maxWindSpeed);
        }
    }

    private String checkResultJson(Double windSpeed, Integer batteryPct, List<ConflictDto> conflicts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", LocalDateTime.now().toString());
        result.put("windSpeed", windSpeed);
        result.put("batteryPct", batteryPct);
        result.put("pass", conflicts.isEmpty());
        result.put("conflicts", conflicts);
        return writeJson(result);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
