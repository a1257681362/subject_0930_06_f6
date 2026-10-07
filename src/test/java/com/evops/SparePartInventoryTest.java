package com.evops;

import com.evops.dto.AcceptRequest;
import com.evops.dto.BladeRequest;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.InboundRequest;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.IssueRequest;
import com.evops.dto.OrderCreateRequest;
import com.evops.dto.PartReplaceRequest;
import com.evops.dto.PartReturnRequest;
import com.evops.dto.PartTraceDto;
import com.evops.dto.ReturnAcceptRequest;
import com.evops.dto.TraceRuleAdjustRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.entity.Blade;
import com.evops.entity.Defect;
import com.evops.entity.InspectionOrder;
import com.evops.entity.PartInbound;
import com.evops.entity.PartInstallation;
import com.evops.entity.PartReplacement;
import com.evops.entity.PartRequisition;
import com.evops.entity.PartReturn;
import com.evops.entity.PartTraceRule;
import com.evops.entity.SparePart;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import com.evops.enums.OrderStatus;
import com.evops.enums.PartDisposition;
import com.evops.enums.PartStatus;
import com.evops.enums.PartType;
import com.evops.enums.RequisitionStatus;
import com.evops.enums.ReturnStatus;
import com.evops.enums.TraceLevel;
import com.evops.enums.VersionStatus;
import com.evops.service.BladeService;
import com.evops.service.InspectionService;
import com.evops.service.SparePartService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F5 风电叶片备件库存：序列批次入库、维修领用绑定风机与缺陷单、
 * 换件记录旧件去向与新件状态、已安装不能再次出库、退库须验收、
 * 库存/维修进度/资产档案同事务更新、规则调整只生成新版本不改旧结论。
 */
@SpringBootTest
@Transactional
class SparePartInventoryTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private BladeService bladeService;
    @Autowired
    private InspectionService inspectionService;
    @Autowired
    private SparePartService sparePartService;

    private Turbine createTurbine(String no) {
        TurbineCreateRequest req = new TurbineCreateRequest();
        req.setTurbineNo(no);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        BladeRequest blade = new BladeRequest();
        blade.setBladeNo(no + "-B1");
        blade.setSerialNo("SN-" + no + "-1");
        blade.setBladePosition(BladePosition.A);
        blade.setMaterial("玻璃纤维增强复合材料");
        req.setBlades(Arrays.asList(blade));
        return turbineService.create(req);
    }

    /** 建档 → 工单 → 提交影像 → 登记缺陷，返回待维修缺陷 */
    private Defect createOpenDefect(String turbineNo) {
        Turbine turbine = createTurbine(turbineNo);
        Blade blade = bladeService.listInServiceAt(turbine.getId(),
                LocalDateTime.of(2026, 1, 1, 0, 0)).get(0);

        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setTurbineId(turbine.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);

        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl("oss://evops/drone/" + turbineNo + "/full.jpg");
        inspect.setInspectTime(LocalDateTime.of(2026, 3, 1, 10, 30));
        inspectionService.submitInspection(order.getId(), inspect);

        DefectRegisterRequest defectReq = new DefectRegisterRequest();
        defectReq.setBladeId(blade.getId());
        defectReq.setDefectType(DefectType.CRACK);
        defectReq.setDefectLevel(DefectLevel.GENERAL);
        defectReq.setDescription("叶根螺栓松动伴随裂纹");
        return inspectionService.registerDefect(order.getId(), defectReq);
    }

    /** 入库一批备件，返回备件列表 */
    private List<SparePart> inbound(String inboundNo, String batchNo, PartType type, String... serials) {
        InboundRequest req = new InboundRequest();
        req.setInboundNo(inboundNo);
        req.setBatchNo(batchNo);
        req.setPartType(type);
        req.setName(type == PartType.BOLT ? "叶片高强螺栓 M36" : type.name());
        req.setSpec("10.9级");
        req.setOperator("库管-张三");
        req.setRemark("月度补货");
        List<InboundRequest.InboundItem> items = new java.util.ArrayList<>();
        for (String serial : serials) {
            InboundRequest.InboundItem item = new InboundRequest.InboundItem();
            item.setSerialNo(serial);
            items.add(item);
        }
        req.setItems(items);
        PartInbound inbound = sparePartService.inbound(req);
        assertEquals(serials.length, inbound.getQuantity());
        // 按序列号取回本批入库的备件
        return sparePartService.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SparePart>()
                .in("serial_no", Arrays.asList(serials))
                .orderByAsc("serial_no"));
    }

    /** 维修领用：绑定风机与缺陷单 */
    private PartRequisition issue(Defect defect, Long turbineId, Long partId, String reqNo) {
        IssueRequest req = new IssueRequest();
        req.setReqNo(reqNo);
        req.setTurbineId(turbineId);
        req.setDefectId(defect.getId());
        req.setPartId(partId);
        req.setIssuedBy("维修班长-李四");
        req.setPurpose("裂纹维修换件");
        return sparePartService.issue(req);
    }

    @Test
    void 入库_按序列号和批次_重复序列号拒绝() {
        List<SparePart> parts = inbound("IN001", "BATCH-2026-01", PartType.BOLT, "BLT-001", "BLT-002");
        assertEquals(2, parts.size());
        assertEquals("BATCH-2026-01", parts.get(0).getBatchNo());
        assertEquals(PartStatus.IN_STOCK, parts.get(0).getStatus());
        assertNotNull(parts.get(0).getStockedAt());
        // 备件编号缺省取 P-序列号
        assertEquals("P-BLT-001", parts.get(0).getPartNo());

        // 入库单留追踪结论，引用当前规则版本
        PartTraceDto trace = sparePartService.trace(parts.get(0).getId());
        PartTraceRule rule = sparePartService.mustGetCurrentRule(PartType.BOLT);
        assertEquals(rule.getId(), trace.getInbound().getRuleVersionId());
        assertTrue(trace.getInbound().getConclusion().contains("规则 v" + rule.getVersionNo()));

        // 序列号全局唯一：重复入库拒绝
        InboundRequest dup = new InboundRequest();
        dup.setInboundNo("IN002");
        dup.setBatchNo("BATCH-2026-02");
        dup.setPartType(PartType.BOLT);
        dup.setName("叶片高强螺栓 M36");
        dup.setOperator("库管-张三");
        InboundRequest.InboundItem item = new InboundRequest.InboundItem();
        item.setSerialNo("BLT-001");
        dup.setItems(Arrays.asList(item));
        assertThrows(IllegalArgumentException.class, () -> sparePartService.inbound(dup));
    }

    @Test
    void 领用_绑定风机与缺陷单_非在库拒绝再次出库() {
        Defect defect = createOpenDefect("T10");
        List<SparePart> parts = inbound("IN010", "BATCH-B10", PartType.BOLT, "BLT-101");
        SparePart part = parts.get(0);
        InspectionOrder order = inspectionService.mustGetOrder(defect.getOrderId());

        PartRequisition requisition = issue(defect, order.getTurbineId(), part.getId(), "RQ010");
        assertEquals(order.getTurbineId(), requisition.getTurbineId());
        assertEquals(defect.getId(), requisition.getDefectId());
        assertEquals(RequisitionStatus.ISSUED, requisition.getStatus());
        assertEquals(PartStatus.ISSUED, sparePartService.mustGet(part.getId()).getStatus());

        // 已领用备件不能再次出库
        Defect defect2 = createOpenDefect("T11");
        InspectionOrder order2 = inspectionService.mustGetOrder(defect2.getOrderId());
        Long partId = part.getId();
        Long turbine2 = order2.getTurbineId();
        Long defect2Id = defect2.getId();
        IssueRequest again = new IssueRequest();
        again.setReqNo("RQ011");
        again.setTurbineId(turbine2);
        again.setDefectId(defect2Id);
        again.setPartId(partId);
        again.setIssuedBy("维修班长-李四");
        assertThrows(IllegalArgumentException.class, () -> sparePartService.issue(again));

        // 缺陷单与风机不匹配拒绝
        List<SparePart> parts2 = inbound("IN011", "BATCH-B11", PartType.BOLT, "BLT-102");
        IssueRequest mismatch = new IssueRequest();
        mismatch.setReqNo("RQ012");
        mismatch.setTurbineId(turbine2);
        mismatch.setDefectId(defect.getId());
        mismatch.setPartId(parts2.get(0).getId());
        mismatch.setIssuedBy("维修班长-李四");
        assertThrows(IllegalArgumentException.class, () -> sparePartService.issue(mismatch));
    }

    @Test
    void 换件_同事务更新库存维修进度与资产档案_记录旧件去向与新件状态() {
        // 第一次换件：装上 BLT-201
        Defect defect1 = createOpenDefect("T20");
        Long turbineId = inspectionService.mustGetOrder(defect1.getOrderId()).getTurbineId();
        SparePart first = inbound("IN020", "BATCH-B20", PartType.BOLT, "BLT-201").get(0);
        PartRequisition rq1 = issue(defect1, turbineId, first.getId(), "RQ020");

        PartReplaceRequest replace1 = new PartReplaceRequest();
        replace1.setReplacedAt(LocalDateTime.of(2026, 4, 1, 9, 0));
        replace1.setOperator("维修班长-李四");
        PartReplacement record1 = sparePartService.replace(rq1.getId(), replace1);

        assertNull(record1.getOldPartId());
        assertEquals(first.getId(), record1.getNewPartId());
        assertEquals("BLT-201", record1.getNewSerialNo());
        assertEquals("INSTALLED", record1.getNewPartStatus());
        // 库存：新件已安装
        assertEquals(PartStatus.INSTALLED, sparePartService.mustGet(first.getId()).getStatus());
        // 维修进度：缺陷与工单推进
        assertEquals(DefectStatus.REPAIRED, inspectionService.mustGetDefect(defect1.getId()).getStatus());
        assertEquals(OrderStatus.REPAIRED, inspectionService.mustGetOrder(defect1.getOrderId()).getStatus());
        // 资产档案：装机台账窗口 [2026-04-01 09:00, null)
        PartTraceDto trace1 = sparePartService.trace(first.getId());
        assertEquals(1, trace1.getInstallations().size());
        assertEquals(LocalDateTime.of(2026, 4, 1, 9, 0),
                trace1.getInstallations().get(0).getInstalledAt());
        assertNull(trace1.getInstallations().get(0).getRemovedAt());

        // 第二次换件：同风机新缺陷，BLT-202 替换 BLT-201，记录旧件去向
        Defect sameTurbineDefect = defectOnTurbine(turbineId, "T20");
        SparePart second = inbound("IN021", "BATCH-B21", PartType.BOLT, "BLT-202").get(0);
        PartRequisition rq2 = issue(sameTurbineDefect, turbineId, second.getId(), "RQ021");

        PartReplaceRequest replace2 = new PartReplaceRequest();
        replace2.setReplacedAt(LocalDateTime.of(2026, 5, 1, 14, 0));
        replace2.setOldDisposition(PartDisposition.SCRAPPED);
        replace2.setDispositionNote("旧螺栓螺纹滑丝，报废处理");
        replace2.setOperator("维修班长-李四");
        PartReplacement record2 = sparePartService.replace(rq2.getId(), replace2);

        // 换件记录：旧件去向与新件状态
        assertEquals(first.getId(), record2.getOldPartId());
        assertEquals("BLT-201", record2.getOldSerialNo());
        assertEquals(PartDisposition.SCRAPPED, record2.getOldDisposition());
        assertEquals(second.getId(), record2.getNewPartId());
        assertEquals("INSTALLED", record2.getNewPartStatus());
        assertTrue(record2.getConclusion().contains("BLT-201"));
        assertTrue(record2.getConclusion().contains("SCRAPPED"));

        // 库存：旧件退役登记去向，新件已安装
        SparePart oldPart = sparePartService.mustGet(first.getId());
        assertEquals(PartStatus.RETIRED, oldPart.getStatus());
        assertEquals(PartDisposition.SCRAPPED, oldPart.getDisposition());
        assertEquals(PartStatus.INSTALLED, sparePartService.mustGet(second.getId()).getStatus());

        // 资产档案：旧件窗口闭合 [04-01, 05-01)，新件窗口 [05-01, null)
        PartTraceDto oldTrace = sparePartService.trace(first.getId());
        PartInstallation oldInstall = oldTrace.getInstallations().get(0);
        assertEquals(LocalDateTime.of(2026, 5, 1, 14, 0), oldInstall.getRemovedAt());
        assertEquals(PartDisposition.SCRAPPED, oldInstall.getDisposition());
        assertEquals("旧螺栓螺纹滑丝，报废处理", oldInstall.getDispositionNote());
        PartTraceDto newTrace = sparePartService.trace(second.getId());
        assertEquals(LocalDateTime.of(2026, 5, 1, 14, 0),
                newTrace.getInstallations().get(0).getInstalledAt());
        assertNull(newTrace.getInstallations().get(0).getRemovedAt());

        // 维修进度：第二个缺陷同步推进
        assertEquals(DefectStatus.REPAIRED,
                inspectionService.mustGetDefect(sameTurbineDefect.getId()).getStatus());
    }

    /** 在指定风机上再造一个待维修缺陷 */
    private Defect defectOnTurbine(Long turbineId, String marker) {
        Blade blade = bladeService.listInServiceAt(turbineId,
                LocalDateTime.of(2026, 1, 1, 0, 0)).get(0);
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setTurbineId(turbineId);
        InspectionOrder order = inspectionService.createOrder(orderReq);
        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl("oss://evops/drone/" + marker + "-2/full.jpg");
        inspect.setInspectTime(LocalDateTime.of(2026, 4, 20, 10, 0));
        inspectionService.submitInspection(order.getId(), inspect);
        DefectRegisterRequest defectReq = new DefectRegisterRequest();
        defectReq.setBladeId(blade.getId());
        defectReq.setDefectType(DefectType.CRACK);
        defectReq.setDefectLevel(DefectLevel.SERIOUS);
        defectReq.setDescription("复检发现螺栓批次性滑丝");
        return inspectionService.registerDefect(order.getId(), defectReq);
    }

    @Test
    void 换件_缺旧件去向被拦截_库存维修进度资产档案均不变() {
        Defect defect1 = createOpenDefect("T30");
        Long turbineId = inspectionService.mustGetOrder(defect1.getOrderId()).getTurbineId();
        SparePart first = inbound("IN030", "BATCH-B30", PartType.BOLT, "BLT-301").get(0);
        PartRequisition rq1 = issue(defect1, turbineId, first.getId(), "RQ030");
        PartReplaceRequest replace1 = new PartReplaceRequest();
        replace1.setReplacedAt(LocalDateTime.of(2026, 4, 1, 9, 0));
        sparePartService.replace(rq1.getId(), replace1);

        // 存在在装旧件但不登记去向 → 拦截
        Defect defect2 = defectOnTurbine(turbineId, "T30");
        SparePart second = inbound("IN031", "BATCH-B31", PartType.BOLT, "BLT-302").get(0);
        PartRequisition rq2 = issue(defect2, turbineId, second.getId(), "RQ031");
        PartReplaceRequest noDisposition = new PartReplaceRequest();
        noDisposition.setReplacedAt(LocalDateTime.of(2026, 5, 1, 14, 0));
        Long rq2Id = rq2.getId();
        assertThrows(IllegalArgumentException.class,
                () -> sparePartService.replace(rq2Id, noDisposition));

        // 同事务：校验不过整体不生效——库存、维修进度、资产档案均保持原状
        assertEquals(PartStatus.ISSUED, sparePartService.mustGet(second.getId()).getStatus());
        assertEquals(PartStatus.INSTALLED, sparePartService.mustGet(first.getId()).getStatus());
        assertEquals(DefectStatus.OPEN, inspectionService.mustGetDefect(defect2.getId()).getStatus());
        assertEquals(1, sparePartService.trace(first.getId()).getInstallations().size());
        assertNull(sparePartService.trace(first.getId()).getInstallations().get(0).getRemovedAt());
        assertEquals(0, sparePartService.trace(second.getId()).getInstallations().size());
    }

    @Test
    void 已安装备件_不能再次出库_也不能退库() {
        Defect defect = createOpenDefect("T40");
        Long turbineId = inspectionService.mustGetOrder(defect.getOrderId()).getTurbineId();
        SparePart part = inbound("IN040", "BATCH-B40", PartType.SENSOR, "SNS-401").get(0);
        PartRequisition rq = issue(defect, turbineId, part.getId(), "RQ040");
        PartReplaceRequest replace = new PartReplaceRequest();
        replace.setReplacedAt(LocalDateTime.of(2026, 4, 1, 9, 0));
        sparePartService.replace(rq.getId(), replace);
        assertEquals(PartStatus.INSTALLED, sparePartService.mustGet(part.getId()).getStatus());

        // 已安装不能再次出库
        Defect defect2 = defectOnTurbine(turbineId, "T40");
        IssueRequest again = new IssueRequest();
        again.setReqNo("RQ041");
        again.setTurbineId(turbineId);
        again.setDefectId(defect2.getId());
        again.setPartId(part.getId());
        again.setIssuedBy("维修班长-李四");
        assertThrows(IllegalArgumentException.class, () -> sparePartService.issue(again));

        // 已安装不能退库
        PartReturnRequest returnReq = new PartReturnRequest();
        returnReq.setReturnNo("RT040");
        returnReq.setReturnedBy("维修班长-李四");
        Long rqId = rq.getId();
        assertThrows(IllegalArgumentException.class, () -> sparePartService.returnPart(rqId, returnReq));
    }

    @Test
    void 退库_须经验收_通过回库_驳回退回已领用() {
        Defect defect = createOpenDefect("T50");
        Long turbineId = inspectionService.mustGetOrder(defect.getOrderId()).getTurbineId();
        SparePart part = inbound("IN050", "BATCH-B50", PartType.COATING_MATERIAL, "CT-501").get(0);
        PartRequisition rq = issue(defect, turbineId, part.getId(), "RQ050");

        // 退库申请：进入待验收
        PartReturnRequest returnReq = new PartReturnRequest();
        returnReq.setReturnNo("RT050");
        returnReq.setReturnedBy("维修班长-李四");
        PartReturn partReturn = sparePartService.returnPart(rq.getId(), returnReq);
        assertEquals(ReturnStatus.PENDING, partReturn.getStatus());
        assertEquals(PartStatus.RETURN_PENDING, sparePartService.mustGet(part.getId()).getStatus());

        // 待验收期间不能再次出库
        Defect defect2 = defectOnTurbine(turbineId, "T50");
        IssueRequest again = new IssueRequest();
        again.setReqNo("RQ051");
        again.setTurbineId(turbineId);
        again.setDefectId(defect2.getId());
        again.setPartId(part.getId());
        again.setIssuedBy("维修班长-李四");
        assertThrows(IllegalArgumentException.class, () -> sparePartService.issue(again));

        // 验收驳回：退回已领用
        ReturnAcceptRequest reject = new ReturnAcceptRequest();
        reject.setPass(false);
        reject.setAcceptedBy("质检-王五");
        reject.setRemark("包装破损，退回维修组");
        partReturn = sparePartService.acceptReturn(partReturn.getId(), reject);
        assertEquals(ReturnStatus.REJECTED, partReturn.getStatus());
        assertEquals(PartStatus.ISSUED, sparePartService.mustGet(part.getId()).getStatus());

        // 再次退库并验收通过：回到在库，领用单闭环
        PartReturnRequest returnReq2 = new PartReturnRequest();
        returnReq2.setReturnNo("RT051");
        returnReq2.setReturnedBy("维修班长-李四");
        PartReturn partReturn2 = sparePartService.returnPart(rq.getId(), returnReq2);
        ReturnAcceptRequest pass = new ReturnAcceptRequest();
        pass.setPass(true);
        pass.setAcceptedBy("质检-王五");
        pass.setRemark("复检合格，重新入库");
        partReturn2 = sparePartService.acceptReturn(partReturn2.getId(), pass);
        assertEquals(ReturnStatus.ACCEPTED, partReturn2.getStatus());
        assertEquals(PartStatus.IN_STOCK, sparePartService.mustGet(part.getId()).getStatus());
        assertEquals(RequisitionStatus.RETURNED,
                sparePartService.mustGetRequisition(rq.getId()).getStatus());

        // 已验收退库单不能重复验收
        ReturnAcceptRequest again2 = new ReturnAcceptRequest();
        again2.setPass(true);
        again2.setAcceptedBy("质检-王五");
        Long returnId = partReturn2.getId();
        assertThrows(IllegalArgumentException.class,
                () -> sparePartService.acceptReturn(returnId, again2));
    }

    @Test
    void 规则调整_只生成新版本_旧版本与旧结论不改动() {
        // 规则 v1 下入库，结论引用 v1
        PartTraceRule v1 = sparePartService.mustGetCurrentRule(PartType.BOLT);
        assertEquals(1, v1.getVersionNo());
        SparePart part1 = inbound("IN060", "BATCH-B60", PartType.BOLT, "BLT-601").get(0);
        PartTraceDto trace1 = sparePartService.trace(part1.getId());
        assertEquals(v1.getId(), trace1.getInbound().getRuleVersionId());
        String oldConclusion = trace1.getInbound().getConclusion();
        assertTrue(oldConclusion.contains("规则 v1"));

        // 规则调整：生成 v2，v1 退役
        TraceRuleAdjustRequest adjust = new TraceRuleAdjustRequest();
        adjust.setTraceLevel(TraceLevel.SERIAL);
        adjust.setNeedInboundCheck(true);
        adjust.setMaxStockMonths(24);
        adjust.setRuleContent("叶片螺栓：按序列号追踪，入库核验合格证、力矩抽检与防锈批次报告");
        adjust.setOperator("总工-赵六");
        PartTraceRule v2 = sparePartService.adjustRule(PartType.BOLT, adjust);
        assertEquals(2, v2.getVersionNo());
        assertEquals(VersionStatus.PUBLISHED, v2.getStatus());

        // 旧版本退役但内容不改动
        List<PartTraceRule> versions = sparePartService.listRules(PartType.BOLT);
        assertEquals(2, versions.size());
        PartTraceRule v1After = versions.get(0);
        assertEquals(VersionStatus.RETIRED, v1After.getStatus());
        assertEquals(v1.getTraceLevel(), v1After.getTraceLevel());
        assertEquals(v1.getRuleContent(), v1After.getRuleContent());
        assertEquals(v1.getMaxStockMonths(), v1After.getMaxStockMonths());

        // 旧结论不随规则调整改变：仍引用 v1，内容不变
        PartTraceDto trace1After = sparePartService.trace(part1.getId());
        assertEquals(v1.getId(), trace1After.getInbound().getRuleVersionId());
        assertEquals(oldConclusion, trace1After.getInbound().getConclusion());

        // 新入库按 v2 生成结论
        SparePart part2 = inbound("IN061", "BATCH-B61", PartType.BOLT, "BLT-602").get(0);
        PartTraceDto trace2 = sparePartService.trace(part2.getId());
        assertEquals(v2.getId(), trace2.getInbound().getRuleVersionId());
        assertTrue(trace2.getInbound().getConclusion().contains("规则 v2"));

        // 当前规则已是 v2
        assertEquals(2, sparePartService.mustGetCurrentRule(PartType.BOLT).getVersionNo());
    }

    @Test
    void 序列追踪_全生命周期可查() {
        Defect defect = createOpenDefect("T60");
        Long turbineId = inspectionService.mustGetOrder(defect.getOrderId()).getTurbineId();
        SparePart part = inbound("IN070", "BATCH-B70", PartType.SENSOR, "SNS-701").get(0);
        PartRequisition rq = issue(defect, turbineId, part.getId(), "RQ070");
        PartReplaceRequest replace = new PartReplaceRequest();
        replace.setReplacedAt(LocalDateTime.of(2026, 4, 1, 9, 0));
        sparePartService.replace(rq.getId(), replace);

        PartTraceDto trace = sparePartService.trace(part.getId());
        assertEquals("SNS-701", trace.getPart().getSerialNo());
        assertEquals("BATCH-B70", trace.getInbound().getBatchNo());
        assertEquals(1, trace.getRequisitions().size());
        assertEquals(defect.getId(), trace.getRequisitions().get(0).getDefectId());
        assertEquals(1, trace.getInstallations().size());
        assertEquals(1, trace.getReplacementsAsNew().size());
        assertEquals(0, trace.getReplacementsAsOld().size());
    }
}
