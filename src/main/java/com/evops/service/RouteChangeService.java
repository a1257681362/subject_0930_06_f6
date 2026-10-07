package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.common.RouteCheckException;
import com.evops.dto.ApprovalRequest;
import com.evops.dto.ConflictDto;
import com.evops.dto.RouteChangeCreateRequest;
import com.evops.dto.RouteVersionRequest;
import com.evops.dto.WaypointDto;
import com.evops.entity.FlightRoute;
import com.evops.entity.FlightRouteVersion;
import com.evops.entity.RouteChangeApproval;
import com.evops.entity.RouteChangeRequest;
import com.evops.enums.ApprovalDecision;
import com.evops.enums.ChangeStatus;
import com.evops.mapper.RouteChangeApprovalMapper;
import com.evops.mapper.RouteChangeRequestMapper;
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
import java.util.Objects;

/**
 * 临时改线：基于基准版本提出新航点串，申请时留存版本差异（diffJson）。
 * 两级审批（1 场站 → 2 运检中心）：只完成一半时保持 PENDING 停留待处理；
 * 任一级驳回即结案；终审通过才生成新版本并发布（发布前校验不过则整体回滚，仍停留待处理）。
 */
@Service
public class RouteChangeService extends ServiceImpl<RouteChangeRequestMapper, RouteChangeRequest> {

    /** 审批级数：1 场站、2 运检中心 */
    public static final int TOTAL_STEPS = 2;

    private final RouteService routeService;
    private final RouteCheckService routeCheckService;
    private final RouteChangeApprovalMapper approvalMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RouteChangeService(RouteService routeService, RouteCheckService routeCheckService,
                              RouteChangeApprovalMapper approvalMapper, AuditService auditService,
                              ObjectMapper objectMapper) {
        this.routeService = routeService;
        this.routeCheckService = routeCheckService;
        this.approvalMapper = approvalMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** 发起改线申请：计算并留存与基准版本的差异 */
    @Transactional
    public RouteChangeRequest create(RouteChangeCreateRequest req) {
        FlightRoute route = routeService.mustGet(req.getRouteId());
        FlightRouteVersion base;
        if (req.getBaseVersionId() != null) {
            base = routeService.mustGetVersion(req.getBaseVersionId());
            if (!base.getRouteId().equals(route.getId())) {
                throw new IllegalArgumentException("基准版本不属于该航线: " + req.getBaseVersionId());
            }
        } else {
            if (route.getCurrentVersionId() == null) {
                throw new IllegalArgumentException("航线尚无已发布版本，无法发起改线: " + route.getId());
            }
            base = routeService.mustGetVersion(route.getCurrentVersionId());
        }

        String changeNo = StringUtils.hasText(req.getChangeNo())
                ? req.getChangeNo()
                : "RC" + System.currentTimeMillis();
        Long dup = count(new QueryWrapper<RouteChangeRequest>().eq("change_no", changeNo));
        if (dup > 0) {
            throw new IllegalArgumentException("改线申请编号已存在: " + changeNo);
        }

        RouteChangeRequest change = new RouteChangeRequest();
        change.setChangeNo(changeNo);
        change.setRouteId(route.getId());
        change.setBaseVersionId(base.getId());
        change.setReason(req.getReason());
        change.setProposedWaypointsJson(writeJson(req.getProposedWaypoints()));
        change.setProposedRequiredBatteryPct(req.getProposedRequiredBatteryPct() != null
                ? req.getProposedRequiredBatteryPct() : base.getRequiredBatteryPct());
        change.setProposedMaxWindSpeed(req.getProposedMaxWindSpeed() != null
                ? req.getProposedMaxWindSpeed() : base.getMaxWindSpeed());
        change.setDiffJson(computeDiff(base, req.getProposedWaypoints(),
                change.getProposedRequiredBatteryPct(), change.getProposedMaxWindSpeed()));
        change.setStatus(ChangeStatus.PENDING);
        change.setCurrentStep(1);
        save(change);

        auditService.record("CHANGE", change.getId(), "CHANGE_CREATE",
                "{\"routeId\":" + route.getId() + ",\"baseVersionNo\":" + base.getVersionNo() + "}", null);
        return change;
    }

    /**
     * 审批当前级次。只完成一半时保持 PENDING 停留待处理；
     * 任一级驳回即结案；终审先做过线校验再落审批记录——校验被拦时不留痕、
     * 申请停留待处理，条件解除后可重新终审。
     */
    @Transactional
    public RouteChangeRequest approve(Long changeId, ApprovalRequest req) {
        RouteChangeRequest change = mustGet(changeId);
        if (change.getStatus() != ChangeStatus.PENDING) {
            throw new IllegalArgumentException("改线审批已结案: " + change.getStatus());
        }
        int step = change.getCurrentStep();
        boolean pass = Boolean.TRUE.equals(req.getPass());

        if (!pass) {
            recordApproval(changeId, step, req, ApprovalDecision.REJECTED);
            change.setStatus(ChangeStatus.REJECTED);
            updateById(change);
            auditService.record("CHANGE", changeId, "CHANGE_REJECTED",
                    "{\"step\":" + step + "}", req.getApprover());
            return change;
        }

        if (step < TOTAL_STEPS) {
            // 只完成一半：记录本级审批、推进级次，停留待处理，不生成新版本
            recordApproval(changeId, step, req, ApprovalDecision.APPROVED);
            change.setCurrentStep(step + 1);
            updateById(change);
            auditService.record("CHANGE", changeId, "CHANGE_STEP_APPROVED",
                    "{\"step\":" + step + ",\"nextStep\":" + (step + 1) + "}", req.getApprover());
            return change;
        }

        // 终审：先对拟改内容做发布前校验（禁飞区必核；风速/电量按填报值复核），
        // 校验不过则不落任何记录，申请停留待处理
        FlightRoute route = routeService.mustGet(change.getRouteId());
        List<WaypointDto> proposed = parseProposed(change);
        List<ConflictDto> conflicts = routeCheckService.check(proposed,
                change.getProposedMaxWindSpeed(), change.getProposedRequiredBatteryPct(),
                req.getWindSpeed(), req.getBatteryPct());
        if (!conflicts.isEmpty()) {
            throw new RouteCheckException(conflicts);
        }
        recordApproval(changeId, step, req, ApprovalDecision.APPROVED);
        RouteVersionRequest versionReq = new RouteVersionRequest();
        versionReq.setWaypoints(proposed);
        versionReq.setRequiredBatteryPct(change.getProposedRequiredBatteryPct());
        versionReq.setMaxWindSpeed(change.getProposedMaxWindSpeed());
        FlightRouteVersion version = routeService.addVersion(route.getId(), versionReq);
        routeService.doPublish(route, version, req.getWindSpeed(), req.getBatteryPct());

        change.setResultingVersionId(version.getId());
        change.setStatus(ChangeStatus.APPROVED);
        updateById(change);
        auditService.record("CHANGE", changeId, "CHANGE_APPROVED",
                "{\"resultingVersionId\":" + version.getId() + "}", req.getApprover());
        return change;
    }

    private void recordApproval(Long changeId, int step, ApprovalRequest req, ApprovalDecision decision) {
        RouteChangeApproval approval = new RouteChangeApproval();
        approval.setChangeId(changeId);
        approval.setStepNo(step);
        approval.setApprover(req.getApprover());
        approval.setDecision(decision);
        approval.setOpinion(req.getOpinion());
        approval.setDecidedAt(LocalDateTime.now());
        approvalMapper.insert(approval);
    }

    /** 申请详情：含拟改航点、版本差异、审批记录 */
    public Map<String, Object> detail(Long changeId) {
        RouteChangeRequest change = mustGet(changeId);
        change.setProposedWaypoints(parseProposed(change));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("change", change);
        result.put("diff", readJson(change.getDiffJson(), new TypeReference<Object>() {
        }));
        result.put("approvals", listApprovals(changeId));
        return result;
    }

    public List<RouteChangeApproval> listApprovals(Long changeId) {
        return approvalMapper.selectList(new QueryWrapper<RouteChangeApproval>()
                .eq("change_id", changeId)
                .orderByAsc("step_no"));
    }

    public List<RouteChangeRequest> listByRoute(Long routeId) {
        return list(new QueryWrapper<RouteChangeRequest>()
                .eq("route_id", routeId)
                .orderByAsc("id"));
    }

    public RouteChangeRequest mustGet(Long id) {
        RouteChangeRequest change = getById(id);
        if (change == null) {
            throw new IllegalArgumentException("改线申请不存在: " + id);
        }
        return change;
    }

    /**
     * 版本差异：按航点序号对比基准版本与拟改内容，
     * 分类 added/removed/moved/unchanged，并记录作业限值参数变化。
     */
    String computeDiff(FlightRouteVersion base, List<WaypointDto> proposed,
                       Integer proposedBatteryPct, Double proposedMaxWindSpeed) {
        List<WaypointDto> baseWaypoints = routeService.parseWaypoints(base);
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> moved = new ArrayList<>();
        int unchanged = 0;

        int max = Math.max(baseWaypoints.size(), proposed.size());
        for (int i = 0; i < max; i++) {
            WaypointDto b = i < baseWaypoints.size() ? baseWaypoints.get(i) : null;
            WaypointDto p = i < proposed.size() ? proposed.get(i) : null;
            if (b == null) {
                added.add(waypointMap(p));
            } else if (p == null) {
                removed.add(waypointMap(b));
            } else if (!samePosition(b, p)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("seq", b.getSeq());
                m.put("from", waypointMap(b));
                m.put("to", waypointMap(p));
                moved.add(m);
            } else {
                unchanged++;
            }
        }

        Map<String, Object> paramChanges = new LinkedHashMap<>();
        if (!Objects.equals(base.getRequiredBatteryPct(), proposedBatteryPct)) {
            paramChanges.put("requiredBatteryPct",
                    fromTo(base.getRequiredBatteryPct(), proposedBatteryPct));
        }
        if (!Objects.equals(base.getMaxWindSpeed(), proposedMaxWindSpeed)) {
            paramChanges.put("maxWindSpeed",
                    fromTo(base.getMaxWindSpeed(), proposedMaxWindSpeed));
        }

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("baseVersionNo", base.getVersionNo());
        diff.put("added", added);
        diff.put("removed", removed);
        diff.put("moved", moved);
        diff.put("unchangedCount", unchanged);
        diff.put("paramChanges", paramChanges);
        return writeJson(diff);
    }

    private boolean samePosition(WaypointDto a, WaypointDto b) {
        return Objects.equals(a.getLat(), b.getLat())
                && Objects.equals(a.getLng(), b.getLng())
                && Objects.equals(a.getAltitudeM(), b.getAltitudeM());
    }

    private Map<String, Object> waypointMap(WaypointDto wp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", wp.getSeq());
        m.put("lat", wp.getLat());
        m.put("lng", wp.getLng());
        m.put("altitudeM", wp.getAltitudeM());
        return m;
    }

    private Map<String, Object> fromTo(Object from, Object to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        return m;
    }

    private List<WaypointDto> parseProposed(RouteChangeRequest change) {
        return readJson(change.getProposedWaypointsJson(), new TypeReference<List<WaypointDto>>() {
        });
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }
}
