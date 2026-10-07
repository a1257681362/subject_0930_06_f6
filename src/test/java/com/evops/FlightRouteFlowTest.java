package com.evops;

import com.evops.common.RouteCheckException;
import com.evops.dto.CheckResultDto;
import com.evops.dto.ConflictDto;
import com.evops.dto.FlightTaskCreateRequest;
import com.evops.dto.GeoPointDto;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.NoFlyZoneRequest;
import com.evops.dto.OrderCreateRequest;
import com.evops.dto.PublishCheckRequest;
import com.evops.dto.RouteCreateRequest;
import com.evops.dto.RouteVersionRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.dto.WaypointDto;
import com.evops.entity.FlightRoute;
import com.evops.entity.FlightRouteVersion;
import com.evops.entity.FlightTask;
import com.evops.entity.InspectionOrder;
import com.evops.entity.NoFlyZone;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.OrderStatus;
import com.evops.enums.TaskStatus;
import com.evops.enums.VersionStatus;
import com.evops.enums.ZoneType;
import com.evops.service.FlightTaskService;
import com.evops.service.InspectionService;
import com.evops.service.NoFlyZoneService;
import com.evops.service.RouteService;
import com.evops.service.TurbineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 航线配置 → 发布前校验（禁飞区/风速/电量）→ 版本作用域 → 历史影像引用原航线 */
@SpringBootTest
@Transactional
class FlightRouteFlowTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private RouteService routeService;
    @Autowired
    private NoFlyZoneService noFlyZoneService;
    @Autowired
    private FlightTaskService flightTaskService;
    @Autowired
    private InspectionService inspectionService;

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

    private FlightRoute createRoute(Turbine turbine, String routeNo) {
        RouteCreateRequest req = new RouteCreateRequest();
        req.setRouteNo(routeNo);
        req.setTurbineId(turbine.getId());
        req.setBladePosition(BladePosition.A);
        req.setShootDistanceM(30.0);
        req.setName("A叶位巡检航线");
        req.setWaypoints(waypoints());
        req.setRequiredBatteryPct(45);
        req.setMaxWindSpeed(10.0);
        return routeService.create(req);
    }

    private FlightRouteVersion v1(FlightRoute route) {
        return routeService.listVersions(route.getId()).get(0);
    }

    private PublishCheckRequest cond(double windSpeed, int batteryPct) {
        PublishCheckRequest req = new PublishCheckRequest();
        req.setWindSpeed(windSpeed);
        req.setBatteryPct(batteryPct);
        return req;
    }

    /** 覆盖 wp2 (41.1510, 115.1010) 的临时禁飞区 */
    private NoFlyZoneRequest zoneCoveringWp2(String zoneNo) {
        NoFlyZoneRequest req = new NoFlyZoneRequest();
        req.setZoneNo(zoneNo);
        req.setName("军演临时管制区");
        req.setZoneType(ZoneType.TEMPORARY);
        req.setPolygon(Arrays.asList(
                gp(41.1505, 115.1005), gp(41.1505, 115.1015),
                gp(41.1515, 115.1015), gp(41.1515, 115.1005)));
        req.setEffectiveFrom(LocalDateTime.now().minusHours(1));
        req.setEffectiveTo(LocalDateTime.now().plusDays(1));
        req.setReason("军演临时管制");
        return req;
    }

    private FlightTask createTask(FlightRoute route, String taskNo) {
        FlightTaskCreateRequest req = new FlightTaskCreateRequest();
        req.setTaskNo(taskNo);
        req.setRouteId(route.getId());
        return flightTaskService.create(req);
    }

    @Test
    void 航线配置与发布_任务绑定当前版本() {
        Turbine turbine = turbine("F201");
        FlightRoute route = createRoute(turbine, "RT-F201-A30");
        FlightRouteVersion v1 = v1(route);
        assertEquals(VersionStatus.DRAFT, v1.getStatus());
        assertEquals(3, v1.getWaypointCount());
        assertTrue(v1.getEstimatedDistanceM() > 0);

        // 同一 风机+方位+拍摄距离 不允许重复配置
        assertThrows(IllegalArgumentException.class, () -> createRoute(turbine, "RT-F201-A30-DUP"));

        // 航线未发布版本，不能创建飞行任务
        assertThrows(IllegalArgumentException.class,
                () -> createTask(route, "FT-F201-000"));

        routeService.publish(v1.getId(), cond(8.0, 90));
        assertEquals(VersionStatus.PUBLISHED, routeService.mustGetVersion(v1.getId()).getStatus());
        assertEquals(v1.getId(), routeService.mustGet(route.getId()).getCurrentVersionId());
        // 校验结果留痕
        assertNotNull(routeService.mustGetVersion(v1.getId()).getCheckResultJson());

        FlightTask task = createTask(route, "FT-F201-001");
        assertEquals(v1.getId(), task.getRouteVersionId());
        assertEquals(TaskStatus.PLANNED, task.getStatus());
        assertEquals(turbine.getId(), task.getTurbineId());

        flightTaskService.takeoff(task.getId());
        assertEquals(TaskStatus.TAKEN_OFF, flightTaskService.mustGet(task.getId()).getStatus());

        // 已发布版本不能重复发布
        assertThrows(IllegalArgumentException.class, () -> routeService.publish(v1.getId(), cond(8.0, 90)));
    }

    @Test
    void 发布前校验_临时禁飞区冲突_给出坐标和原因() {
        Turbine turbine = turbine("F202");
        FlightRoute route = createRoute(turbine, "RT-F202-A30");
        FlightRouteVersion v1 = v1(route);
        NoFlyZone zone = noFlyZoneService.create(zoneCoveringWp2("NZ-202"));

        RouteCheckException ex = assertThrows(RouteCheckException.class,
                () -> routeService.publish(v1.getId(), cond(8.0, 90)));
        List<ConflictDto> conflicts = ex.getConflicts();
        assertEquals(1, conflicts.size());
        ConflictDto conflict = conflicts.get(0);
        assertEquals("NO_FLY_ZONE", conflict.getType());
        // 冲突区域给出坐标和原因
        assertEquals(41.1510, conflict.getLat());
        assertEquals(115.1010, conflict.getLng());
        assertEquals(2, conflict.getWaypointSeq());
        assertEquals("军演临时管制区", conflict.getZoneName());
        assertTrue(conflict.getReason().contains("临时禁飞区"));
        assertTrue(conflict.getReason().contains("军演临时管制"));

        // 冲突未解除，版本仍是草稿；取消禁飞区后放行
        assertEquals(VersionStatus.DRAFT, routeService.mustGetVersion(v1.getId()).getStatus());
        noFlyZoneService.cancel(zone.getId());
        routeService.publish(v1.getId(), cond(8.0, 90));
        assertEquals(VersionStatus.PUBLISHED, routeService.mustGetVersion(v1.getId()).getStatus());
    }

    @Test
    void 发布前校验_风速超限与电量余量不足() {
        Turbine turbine = turbine("F203");
        FlightRoute route = createRoute(turbine, "RT-F203-A30");
        FlightRouteVersion v1 = v1(route);

        // 风速 12.5 > 限值 10；电量 60 - 预计耗电 45 = 15 < 安全余量 20
        CheckResultDto result = routeService.check(v1.getId(), cond(12.5, 60));
        assertEquals(false, result.isPass());
        assertEquals(2, result.getConflicts().size());
        assertEquals("WIND", result.getConflicts().get(0).getType());
        assertTrue(result.getConflicts().get(0).getReason().contains("12.5"));
        assertEquals("BATTERY", result.getConflicts().get(1).getType());
        assertTrue(result.getConflicts().get(1).getReason().contains("45%"));

        // 试算留痕，状态不推进
        assertEquals(VersionStatus.DRAFT, routeService.mustGetVersion(v1.getId()).getStatus());
        assertNotNull(routeService.mustGetVersion(v1.getId()).getCheckResultJson());

        // 条件满足后发布成功
        routeService.publish(v1.getId(), cond(9.0, 70));
        assertEquals(VersionStatus.PUBLISHED, routeService.mustGetVersion(v1.getId()).getStatus());
    }

    @Test
    void 禁飞区窗口外不拦截() {
        Turbine turbine = turbine("F204");
        FlightRoute route = createRoute(turbine, "RT-F204-A30");
        FlightRouteVersion v1 = v1(route);

        // 已过期
        NoFlyZoneRequest expired = zoneCoveringWp2("NZ-204-1");
        expired.setEffectiveFrom(LocalDateTime.now().minusDays(2));
        expired.setEffectiveTo(LocalDateTime.now().minusDays(1));
        noFlyZoneService.create(expired);
        // 尚未生效
        NoFlyZoneRequest future = zoneCoveringWp2("NZ-204-2");
        future.setEffectiveFrom(LocalDateTime.now().plusDays(1));
        future.setEffectiveTo(LocalDateTime.now().plusDays(2));
        noFlyZoneService.create(future);

        routeService.publish(v1.getId(), cond(8.0, 90));
        assertEquals(VersionStatus.PUBLISHED, routeService.mustGetVersion(v1.getId()).getStatus());
    }

    @Test
    void 新版本只作用于未起飞任务_历史影像引用原航线() {
        Turbine turbine = turbine("F205");
        FlightRoute route = createRoute(turbine, "RT-F205-A30");
        FlightRouteVersion v1 = v1(route);
        routeService.publish(v1.getId(), cond(8.0, 90));

        FlightTask taskA = createTask(route, "FT-F205-A"); // 未起飞
        FlightTask taskB = createTask(route, "FT-F205-B"); // 先起飞
        flightTaskService.takeoff(taskB.getId());

        // 发布 v2：航点平移
        RouteVersionRequest v2Req = new RouteVersionRequest();
        v2Req.setWaypoints(Arrays.asList(
                wp(1, 41.1501, 115.1001, 80.0),
                wp(2, 41.1511, 115.1011, 80.0),
                wp(3, 41.1521, 115.1001, 75.0)));
        v2Req.setRequiredBatteryPct(45);
        v2Req.setMaxWindSpeed(10.0);
        FlightRouteVersion v2 = routeService.addVersion(route.getId(), v2Req);
        routeService.publish(v2.getId(), cond(8.0, 90));

        // 未起飞任务改绑 v2；已起飞任务保持 v1
        assertEquals(v2.getId(), flightTaskService.mustGet(taskA.getId()).getRouteVersionId());
        assertEquals(v1.getId(), flightTaskService.mustGet(taskB.getId()).getRouteVersionId());
        // 旧版本退役但保留
        assertEquals(VersionStatus.RETIRED, routeService.mustGetVersion(v1.getId()).getStatus());

        // 未起飞任务不能关联巡检工单
        FlightTask finalTaskA = flightTaskService.mustGet(taskA.getId());
        OrderCreateRequest plannedOrder = new OrderCreateRequest();
        plannedOrder.setTurbineId(turbine.getId());
        plannedOrder.setFlightTaskId(finalTaskA.getId());
        assertThrows(IllegalArgumentException.class, () -> inspectionService.createOrder(plannedOrder));

        // 历史影像：taskB 拍摄的工单继续引用原航线 v1
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setOrderNo("IO-F205-001");
        orderReq.setTurbineId(turbine.getId());
        orderReq.setFlightTaskId(taskB.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);
        assertEquals(taskB.getId(), order.getFlightTaskId());
        assertEquals(v1.getId(), order.getRouteVersionId());

        // 提交影像后引用不变
        InspectSubmitRequest submit = new InspectSubmitRequest();
        submit.setDroneImageUrl("oss://evops/F205/001.zip");
        submit.setInspectTime(LocalDateTime.now());
        inspectionService.submitInspection(order.getId(), submit);
        InspectionOrder inspected = inspectionService.mustGetOrder(order.getId());
        assertEquals(OrderStatus.INSPECTED, inspected.getStatus());
        assertEquals(v1.getId(), inspected.getRouteVersionId());

        // 工单风机与飞行任务风机不一致时被拦截
        Turbine other = turbine("F206");
        OrderCreateRequest mismatch = new OrderCreateRequest();
        mismatch.setTurbineId(other.getId());
        mismatch.setFlightTaskId(taskB.getId());
        assertThrows(IllegalArgumentException.class, () -> inspectionService.createOrder(mismatch));
    }
}
