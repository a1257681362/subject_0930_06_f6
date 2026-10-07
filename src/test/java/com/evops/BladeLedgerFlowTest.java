package com.evops;

import com.evops.dto.AcceptRequest;
import com.evops.dto.BladeRequest;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.OrderCreateRequest;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReplacementRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.entity.Blade;
import com.evops.entity.BladeReplacement;
import com.evops.entity.Defect;
import com.evops.entity.InspectionOrder;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.BladeStatus;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import com.evops.enums.OrderStatus;
import com.evops.service.BladeService;
import com.evops.service.InspectionService;
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

/** 建档 → 巡检 → 缺陷登记 → 维修 → 验收 全流程，以及编号唯一与换件前后关系 */
@SpringBootTest
@Transactional
class BladeLedgerFlowTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private BladeService bladeService;
    @Autowired
    private InspectionService inspectionService;

    private BladeRequest blade(String no, String serial, BladePosition pos) {
        BladeRequest req = new BladeRequest();
        req.setBladeNo(no);
        req.setSerialNo(serial);
        req.setBladePosition(pos);
        req.setMaterial("玻璃纤维增强复合材料");
        return req;
    }

    private Turbine createTurbine(String no) {
        TurbineCreateRequest req = new TurbineCreateRequest();
        req.setTurbineNo(no);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        req.setBlades(Arrays.asList(
                blade(no + "-B1", "SN-" + no + "-1", BladePosition.A),
                blade(no + "-B2", "SN-" + no + "-2", BladePosition.B),
                blade(no + "-B3", "SN-" + no + "-3", BladePosition.C)));
        return turbineService.create(req);
    }

    @Test
    void 建档_风机与首批叶片() {
        Turbine turbine = createTurbine("F01");
        assertNotNull(turbine.getId());
        assertEquals("张北坝上风电场", turbine.getSite());
        assertEquals("WT2000-D110", turbine.getModel());

        List<Blade> blades = bladeService.listInServiceAt(turbine.getId(),
                LocalDateTime.of(2026, 1, 1, 0, 0));
        assertEquals(3, blades.size());
        // 未指定装上时刻时默认取并网时间，与风机档案同一口径
        assertEquals(turbine.getGridConnectedAt(), blades.get(0).getInstalledAt());
    }

    @Test
    void 编号唯一_风机与叶片互斥() {
        Turbine f02 = createTurbine("F02");

        // 风机编号重复
        TurbineCreateRequest dup = new TurbineCreateRequest();
        dup.setTurbineNo("F02");
        dup.setSite("场址");
        dup.setModel("机型");
        dup.setGridConnectedAt(LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> turbineService.create(dup));

        // 风机编号与已有叶片编号重复
        dup.setTurbineNo("F02-B1");
        assertThrows(IllegalArgumentException.class, () -> turbineService.create(dup));

        // 叶片编号与已有风机编号重复
        assertThrows(IllegalArgumentException.class,
                () -> bladeService.addBlade(f02.getId(), blade("F02", "SN-NEW-1", BladePosition.A)));

        // 叶片序列号重复
        assertThrows(IllegalArgumentException.class,
                () -> bladeService.addBlade(f02.getId(), blade("F02-B9", "SN-F02-1", BladePosition.A)));
    }

    @Test
    void 巡检_缺陷_维修_验收_全流程() {
        Turbine turbine = createTurbine("F10");
        Blade bladeA = bladeService.findInService(turbine.getId(), BladePosition.A);

        // 巡检：创建工单并提交无人机影像
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setOrderNo("IO-F10-001");
        orderReq.setTurbineId(turbine.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);
        assertEquals(OrderStatus.PENDING, order.getStatus());

        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl("oss://evops/drone/F10/20260301/a1.jpg");
        inspect.setInspectTime(LocalDateTime.of(2026, 3, 1, 10, 30));
        order = inspectionService.submitInspection(order.getId(), inspect);
        assertEquals(OrderStatus.INSPECTED, order.getStatus());
        assertEquals("oss://evops/drone/F10/20260301/a1.jpg", order.getDroneImageUrl());

        // 缺陷登记
        DefectRegisterRequest defectReq = new DefectRegisterRequest();
        defectReq.setBladeId(bladeA.getId());
        defectReq.setDefectType(DefectType.COATING_PEELING);
        defectReq.setDefectLevel(DefectLevel.SERIOUS);
        defectReq.setDescription("叶尖前缘腐蚀，长度约 35cm");
        Defect defect = inspectionService.registerDefect(order.getId(), defectReq);
        assertEquals(DefectStatus.OPEN, defect.getStatus());
        assertEquals(OrderStatus.DEFECT_REGISTERED,
                inspectionService.mustGetOrder(order.getId()).getStatus());

        // 维修
        RepairRequest repair = new RepairRequest();
        repair.setRepairResult("打磨后双组分胶衣修补，固化 24h 复检合格");
        defect = inspectionService.repair(defect.getId(), repair);
        assertEquals(DefectStatus.REPAIRED, defect.getStatus());
        assertNotNull(defect.getRepairedAt());

        // 验收驳回 → 退回待维修
        AcceptRequest reject = new AcceptRequest();
        reject.setPass(false);
        reject.setRemark("修补区边缘仍有气泡，返工");
        defect = inspectionService.accept(defect.getId(), reject);
        assertEquals(DefectStatus.OPEN, defect.getStatus());

        // 返工后再次维修并验收通过
        defect = inspectionService.repair(defect.getId(), repair);
        AcceptRequest pass = new AcceptRequest();
        pass.setPass(true);
        pass.setRemark("复检合格");
        defect = inspectionService.accept(defect.getId(), pass);
        assertEquals(DefectStatus.ACCEPTED, defect.getStatus());
        assertNotNull(defect.getAcceptedAt());

        // 全部缺陷验收通过，工单闭环
        assertEquals(OrderStatus.ACCEPTED, inspectionService.mustGetOrder(order.getId()).getStatus());
    }

    @Test
    void 无缺陷工单_直接闭环() {
        Turbine turbine = createTurbine("F11");
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setTurbineId(turbine.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);

        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl("oss://evops/drone/F11/all.jpg");
        inspect.setInspectTime(LocalDateTime.of(2026, 3, 2, 9, 0));
        inspectionService.submitInspection(order.getId(), inspect);

        assertEquals(OrderStatus.CLOSED, inspectionService.close(order.getId()).getStatus());
    }

    @Test
    void 换件_保留前后关系() {
        Turbine turbine = createTurbine("F20");
        Blade oldBlade = bladeService.findInService(turbine.getId(), BladePosition.B);

        ReplacementRequest req = new ReplacementRequest();
        req.setBladePosition(BladePosition.B);
        req.setNewBlade(blade("F20-B2R", "SN-F20-2R", BladePosition.B));
        req.setReplacedAt(LocalDateTime.of(2026, 6, 15, 14, 0));
        req.setReason("雷击损伤更换");
        BladeReplacement record = bladeService.replace(turbine.getId(), req);

        // 前后关系保留
        assertEquals(oldBlade.getId(), record.getOldBladeId());
        assertNotNull(record.getNewBladeId());

        // 旧叶片下架，归属窗口闭合
        Blade removed = bladeService.mustGet(oldBlade.getId());
        assertEquals(BladeStatus.REPLACED, removed.getStatus());
        assertEquals(req.getReplacedAt(), removed.getRemovedAt());

        // 新叶片上架，归属窗口自换件时刻起
        Blade current = bladeService.findInService(turbine.getId(), BladePosition.B);
        assertEquals("F20-B2R", current.getBladeNo());
        assertEquals(req.getReplacedAt(), current.getInstalledAt());

        // 换件记录可溯
        List<BladeReplacement> history = bladeService.listReplacements(turbine.getId());
        assertEquals(1, history.size());
        assertEquals(record.getId(), history.get(0).getId());
    }

    @Test
    void 状态机_未巡检不能登记缺陷() {
        Turbine turbine = createTurbine("F30");
        Blade bladeA = bladeService.findInService(turbine.getId(), BladePosition.A);

        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setTurbineId(turbine.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);

        DefectRegisterRequest defectReq = new DefectRegisterRequest();
        defectReq.setBladeId(bladeA.getId());
        defectReq.setDefectType(DefectType.CRACK);
        defectReq.setDefectLevel(DefectLevel.MINOR);
        assertThrows(IllegalArgumentException.class,
                () -> inspectionService.registerDefect(order.getId(), defectReq));

        // 未维修不能验收
        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl("oss://evops/drone/F30/a.jpg");
        inspect.setInspectTime(LocalDateTime.now());
        inspectionService.submitInspection(order.getId(), inspect);
        Defect defect = inspectionService.registerDefect(order.getId(), defectReq);
        AcceptRequest pass = new AcceptRequest();
        pass.setPass(true);
        assertThrows(IllegalArgumentException.class,
                () -> inspectionService.accept(defect.getId(), pass));

        assertTrue(inspectionService.listDefects(order.getId()).size() > 0);
    }
}
