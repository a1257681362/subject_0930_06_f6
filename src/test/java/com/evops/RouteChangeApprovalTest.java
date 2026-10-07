package com.evops;

import com.evops.common.RouteCheckException;
import com.evops.dto.ApprovalRequest;
import com.evops.dto.ConflictDto;
import com.evops.dto.FlightTaskCreateRequest;
import com.evops.dto.GeoPointDto;
import com.evops.dto.NoFlyZoneRequest;
import com.evops.dto.PublishCheckRequest;
import com.evops.dto.RouteChangeCreateRequest;
import com.evops.dto.RouteCreateRequest;
import com.evops.dto.RouteVersionRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.dto.WaypointDto;
import com.evops.entity.AuditEvent;
import com.evops.entity.FlightRoute;
import com.evops.entity.FlightRouteVersion;
import com.evops.entity.FlightTask;
import com.evops.entity.NoFlyZone;
import com.evops.entity.RouteChangeApproval;
import com.evops.entity.RouteChangeRequest;
import com.evops.entity.Turbine;
import com.evops.enums.ApprovalDecision;
import com.evops.enums.BladePosition;
import com.evops.enums.ChangeStatus;
import com.evops.enums.VersionStatus;
import com.evops.enums.ZoneStatus;
import com.evops.enums.ZoneType;
import com.evops.service.AuditService;
import com.evops.service.FlightTaskService;
import com.evops.service.NoFlyZoneService;
import com.evops.service.RouteChangeService;
import com.evops.service.RouteService;
import com.evops.service.TurbineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 临时改线：两级审批（半批停留待处理）、版本差异、禁飞区拦截、全程可追溯 */
@SpringBootTest
@Transactional
class RouteChangeApprovalTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private RouteService routeService;
    @Autowired
    private NoFlyZoneService noFlyZoneService;
    @Autowired
    private FlightTaskService flightTaskService;
    @Autowired
    private RouteChangeService routeChangeService;
    @Autowired
    private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Turbine turbine(String no) {
        TurbineCreateRequest req = new TurbineCreateRequest();
        req.setTurbineNo(no);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return turbineService.create(req);
    }

    private WaypointDto wp(int seq, double lat, double lng, double altitude) {
        WaypointDto wp = new WaypointDto();
        wp.setSeq(seq);
        wp.setLat(lat);
        wp.setLng(lng);
        wp.setAltitudeM(altitude);
        wp.setAction("SHOOT");
        return wp;
    }

    private GeoPointDto gp(double lat, double lng) {
        GeoPointDto p = new GeoPointDto();
        p.setLat(lat);
        p.setLng(lng);
        return p;
    }

    private List<WaypointDto> waypoints() {
        return Arrays.asList(
                wp(1, 41.1500, 115.1000, 80.0),
                wp(2, 41.1510, 115.1010, 80.0),
                wp(3, 41.1520, 115.1000, 75.0));
    }

    /** 发布 v1 的航线 */
    private FlightRoute publishedRoute(String turbineNo, String routeNo) {
        Turbine turbine = turbine(turbineNo);
        RouteCreateRequest req = new RouteCreateRequest();
        req.setRouteNo(routeNo);
        req.setTurbineId(turbine.getId());
        req.setBladePosition(BladePosition.A);
        req.setShootDistanceM(30.0);
        req.setWaypoints(waypoints());
        req.setRequiredBatteryPct(45);
        req.setMaxWindSpeed(10.0);
        FlightRoute route = routeService.create(req);
        PublishCheckRequest cond = new PublishCheckRequest();
        cond.setWindSpeed(8.0);
        cond.setBatteryPct(90);
        routeService.publish(routeService.listVersions(route.getId()).get(0).getId(), cond);
        return routeService.mustGet(route.getId());
    }

    /** 拟改内容：wp2 移位、新增 wp4、耗电 45→50 */
    private RouteChangeCreateRequest changeReq(FlightRoute route, String changeNo) {
        RouteChangeCreateRequest req = new RouteChangeCreateRequest();
        req.setChangeNo(changeNo);
        req.setRouteId(route.getId());
        req.setReason("避让施工塔吊");
        req.setProposedWaypoints(Arrays.asList(
                wp(1, 41.1500, 115.1000, 80.0),
                wp(2, 41.1512, 115.1012, 82.0),
                wp(3, 41.1520, 115.1000, 75.0),
                wp(4, 41.1525, 115.1005, 75.0)));
        req.setProposedRequiredBatteryPct(50);
        return req;
    }

    private ApprovalRequest approve(String approver, boolean pass) {
        ApprovalRequest req = new ApprovalRequest();
        req.setApprover(approver);
        req.setPass(pass);
        return req;
    }

    @Test
    void 临时改线_两级审批_半批停留待处理() throws Exception {
        FlightRoute route = publishedRoute("F301", "RT-F301-A30");
        FlightRouteVersion v1 = routeService.mustGetVersion(route.getCurrentVersionId());
        FlightTaskCreateRequest taskReq = new FlightTaskCreateRequest();
        taskReq.setTaskNo("FT-F301-001");
        taskReq.setRouteId(route.getId());
        FlightTask task = flightTaskService.create(taskReq);

        // 发起改线：留存版本差异
        RouteChangeRequest change = routeChangeService.create(changeReq(route, "RC-F301-001"));
        assertEquals(ChangeStatus.PENDING, change.getStatus());
        assertEquals(1, change.getCurrentStep());
        JsonNode diff = objectMapper.readTree(change.getDiffJson());
        assertEquals(1, diff.get("baseVersionNo").asInt());
        assertEquals(1, diff.get("moved").size());
        assertEquals(2, diff.get("moved").get(0).get("seq").asInt());
        assertEquals(1, diff.get("added").size());
        assertEquals(2, diff.get("unchangedCount").asInt());
        assertEquals(45, diff.get("paramChanges").get("requiredBatteryPct").get("from").asInt());
        assertEquals(50, diff.get("paramChanges").get("requiredBatteryPct").get("to").asInt());

        // 第一级审批通过：只完成一半，停留待处理，不生成新版本
        routeChangeService.approve(change.getId(), approve("场站-张三", true));
        RouteChangeRequest half = routeChangeService.mustGet(change.getId());
        assertEquals(ChangeStatus.PENDING, half.getStatus());
        assertEquals(2, half.getCurrentStep());
        assertEquals(v1.getId(), routeService.mustGet(route.getId()).getCurrentVersionId());
        assertEquals(1, routeService.listVersions(route.getId()).size());

        // 第二级审批通过：结案并生成 v2 发布，未起飞任务改绑
        ApprovalRequest finalApproval = approve("运检-李四", true);
        finalApproval.setWindSpeed(8.0);
        finalApproval.setBatteryPct(80);
        routeChangeService.approve(change.getId(), finalApproval);
        RouteChangeRequest done = routeChangeService.mustGet(change.getId());
        assertEquals(ChangeStatus.APPROVED, done.getStatus());
        assertNotNull(done.getResultingVersionId());
        assertEquals(done.getResultingVersionId(), routeService.mustGet(route.getId()).getCurrentVersionId());
        assertEquals(2, routeService.listVersions(route.getId()).size());
        assertEquals(done.getResultingVersionId(),
                flightTaskService.mustGet(task.getId()).getRouteVersionId());

        // 审批记录完整可追溯
        List<RouteChangeApproval> approvals = routeChangeService.listApprovals(change.getId());
        assertEquals(2, approvals.size());
        assertEquals(1, approvals.get(0).getStepNo());
        assertEquals("场站-张三", approvals.get(0).getApprover());
        assertEquals(ApprovalDecision.APPROVED, approvals.get(0).getDecision());
        assertEquals(2, approvals.get(1).getStepNo());
        assertEquals("运检-李四", approvals.get(1).getApprover());

        // 已结案不能再批
        assertThrows(IllegalArgumentException.class,
                () -> routeChangeService.approve(change.getId(), approve("运检-王五", true)));
    }

    @Test
    void 改线申请_任一级驳回即结案() {
        FlightRoute route = publishedRoute("F302", "RT-F302-A30");
        RouteChangeRequest change = routeChangeService.create(changeReq(route, "RC-F302-001"));

        ApprovalRequest reject = approve("场站-张三", false);
        reject.setOpinion("风险未评估");
        routeChangeService.approve(change.getId(), reject);

        RouteChangeRequest rejected = routeChangeService.mustGet(change.getId());
        assertEquals(ChangeStatus.REJECTED, rejected.getStatus());
        // 驳回不推进航线版本
        assertEquals(1, routeService.listVersions(route.getId()).size());
        // 已结案不能再批
        assertThrows(IllegalArgumentException.class,
                () -> routeChangeService.approve(change.getId(), approve("运检-李四", true)));
    }

    @Test
    void 改线遇临时禁飞区_终审被拦并停留待处理() {
        FlightRoute route = publishedRoute("F303", "RT-F303-A30");
        // 禁飞区覆盖拟改后的 wp2 (41.1512, 115.1012)
        NoFlyZoneRequest zoneReq = new NoFlyZoneRequest();
        zoneReq.setZoneNo("NZ-303");
        zoneReq.setName("活动临时管制区");
        zoneReq.setZoneType(ZoneType.TEMPORARY);
        zoneReq.setPolygon(Arrays.asList(
                gp(41.1510, 115.1010), gp(41.1510, 115.1015),
                gp(41.1515, 115.1015), gp(41.1515, 115.1010)));
        zoneReq.setEffectiveFrom(LocalDateTime.now().minusHours(1));
        zoneReq.setEffectiveTo(LocalDateTime.now().plusHours(2));
        zoneReq.setReason("群众活动临时管制");
        NoFlyZone zone = noFlyZoneService.create(zoneReq);

        RouteChangeRequest change = routeChangeService.create(changeReq(route, "RC-F303-001"));
        routeChangeService.approve(change.getId(), approve("场站-张三", true));

        // 终审撞上生效中的临时禁飞区：冲突带坐标和原因，申请停留待处理
        RouteCheckException ex = assertThrows(RouteCheckException.class,
                () -> routeChangeService.approve(change.getId(), approve("运检-李四", true)));
        List<ConflictDto> conflicts = ex.getConflicts();
        assertEquals(1, conflicts.size());
        assertEquals(41.1512, conflicts.get(0).getLat());
        assertEquals(115.1012, conflicts.get(0).getLng());
        assertTrue(conflicts.get(0).getReason().contains("活动临时管制区"));

        RouteChangeRequest pending = routeChangeService.mustGet(change.getId());
        assertEquals(ChangeStatus.PENDING, pending.getStatus());
        assertEquals(2, pending.getCurrentStep());
        assertEquals(1, routeService.listVersions(route.getId()).size());

        // 禁飞区取消后终审放行
        noFlyZoneService.cancel(zone.getId());
        routeChangeService.approve(change.getId(), approve("运检-李四", true));
        assertEquals(ChangeStatus.APPROVED, routeChangeService.mustGet(change.getId()).getStatus());
        assertEquals(2, routeService.listVersions(route.getId()).size());
    }

    @Test
    void 航线规划与禁飞区可追溯() {
        FlightRoute route = publishedRoute("F304", "RT-F304-A30");

        // 航线动作留痕
        List<String> routeActions = auditService.listByEntity("ROUTE", route.getId())
                .stream().map(AuditEvent::getAction).collect(Collectors.toList());
        assertTrue(routeActions.contains("ROUTE_CREATE"));
        assertTrue(routeActions.contains("VERSION_PUBLISH"));

        // 禁飞区动作留痕：划定、取消都可查，区域记录不删除
        NoFlyZoneRequest zoneReq = new NoFlyZoneRequest();
        zoneReq.setZoneNo("NZ-304");
        zoneReq.setName("历史管制区");
        zoneReq.setZoneType(ZoneType.TEMPORARY);
        zoneReq.setPolygon(Arrays.asList(
                gp(41.0, 115.0), gp(41.0, 115.001), gp(41.001, 115.001)));
        zoneReq.setEffectiveFrom(LocalDateTime.now().minusDays(10));
        zoneReq.setEffectiveTo(LocalDateTime.now().minusDays(9));
        NoFlyZone zone = noFlyZoneService.create(zoneReq);
        noFlyZoneService.cancel(zone.getId());

        List<String> zoneActions = auditService.listByEntity("ZONE", zone.getId())
                .stream().map(AuditEvent::getAction).collect(Collectors.toList());
        assertTrue(zoneActions.contains("ZONE_CREATE"));
        assertTrue(zoneActions.contains("ZONE_CANCEL"));
        assertEquals(ZoneStatus.CANCELLED, noFlyZoneService.mustGet(zone.getId()).getStatus());
        assertTrue(noFlyZoneService.listAll().stream().anyMatch(z -> z.getId().equals(zone.getId())));
    }

    @Test
    void 历史版本退役后仍可追溯航点() {
        FlightRoute route = publishedRoute("F305", "RT-F305-A30");
        FlightRouteVersion v1 = routeService.mustGetVersion(route.getCurrentVersionId());

        RouteVersionRequest v2Req = new RouteVersionRequest();
        v2Req.setWaypoints(Arrays.asList(
                wp(1, 41.1501, 115.1001, 80.0),
                wp(2, 41.1511, 115.1011, 80.0),
                wp(3, 41.1521, 115.1001, 75.0)));
        v2Req.setRequiredBatteryPct(45);
        v2Req.setMaxWindSpeed(10.0);
        FlightRouteVersion v2 = routeService.addVersion(route.getId(), v2Req);
        PublishCheckRequest cond = new PublishCheckRequest();
        cond.setWindSpeed(8.0);
        cond.setBatteryPct(90);
        routeService.publish(v2.getId(), cond);

        FlightRouteVersion retired = routeService.versionDetail(v1.getId());
        assertEquals(VersionStatus.RETIRED, retired.getStatus());
        assertEquals(3, retired.getWaypoints().size());
        assertEquals(41.1510, retired.getWaypoints().get(1).getLat());
    }
}
