package com.evops;

import com.evops.dto.AcceptRequest;
import com.evops.dto.AnnotationRequest;
import com.evops.dto.AnnotationTraceDto;
import com.evops.dto.BladeRequest;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.DisposeRuleUpdateRequest;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.NoteRequest;
import com.evops.dto.OrderCreateRequest;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReviewRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.entity.Blade;
import com.evops.entity.Defect;
import com.evops.entity.DefectAnnotation;
import com.evops.entity.DefectDisposeRule;
import com.evops.entity.DefectNote;
import com.evops.entity.InspectionOrder;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import com.evops.enums.OrderStatus;
import com.evops.service.BladeService;
import com.evops.service.DefectManageService;
import com.evops.service.InspectionService;
import com.evops.service.TurbineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3 叶片缺陷管理：分类时限配置、影像标注、专家复核（不覆盖初判）、
 * 已关闭只能追加说明、影像标注反查。
 */
@SpringBootTest
@Transactional
class DefectManagementTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private BladeService bladeService;
    @Autowired
    private InspectionService inspectionService;
    @Autowired
    private DefectManageService defectManageService;

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

    /** 建档 → 工单 → 提交影像 → 登记缺陷，返回缺陷 */
    private Defect registerDefect(String turbineNo, DefectType type, DefectLevel level, String desc) {
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
        defectReq.setDefectType(type);
        defectReq.setDefectLevel(level);
        defectReq.setDescription(desc);
        return inspectionService.registerDefect(order.getId(), defectReq);
    }

    @Test
    void 处置时限配置_预置四类型四等级() {
        List<DefectDisposeRule> rules = defectManageService.listRules();
        assertEquals(16, rules.size());

        // 调整时限与建议模板
        DefectDisposeRule rule = defectManageService.mustGetRule(DefectType.CRACK, DefectLevel.CRITICAL);
        DisposeRuleUpdateRequest update = new DisposeRuleUpdateRequest();
        update.setDisposeHours(12);
        update.setAdviceTemplate("裂纹危急：立即停机，12小时内完成更换");
        DefectDisposeRule updated = defectManageService.updateRule(rule.getId(), update);
        assertEquals(12, updated.getDisposeHours());
        assertEquals("裂纹危急：立即停机，12小时内完成更换", updated.getAdviceTemplate());
    }

    @Test
    void 登记缺陷_按类型等级生成时限与建议() {
        LocalDateTime before = LocalDateTime.now();
        Defect defect = registerDefect("D01", DefectType.CRACK, DefectLevel.SERIOUS, "叶中裂纹长约 20cm");

        assertEquals(DefectType.CRACK, defect.getDefectType());
        assertEquals(DefectLevel.SERIOUS, defect.getInitialLevel());
        assertEquals(DefectLevel.SERIOUS, defect.getDefectLevel());
        assertEquals("裂纹严重：7日内停机检查，评估结构补强或叶尖段更换", defect.getRepairAdvice());
        assertNotNull(defect.getDisposeDeadline());
        // 处置时限 = 登记时刻 + 168h
        assertTrue(defect.getDisposeDeadline().isAfter(before.plusHours(167)));
        assertTrue(defect.getDisposeDeadline().isBefore(LocalDateTime.now().plusHours(169)));
    }

    @Test
    void 影像标注_位置尺寸截图() {
        Defect defect = registerDefect("D02", DefectType.LIGHTNING, DefectLevel.GENERAL, "接闪器烧蚀");

        AnnotationRequest req = new AnnotationRequest();
        req.setPositionDesc("距叶尖 2.3m 接闪器处");
        req.setSizeDesc("烧蚀斑直径 45mm");
        req.setScreenshotUrl("oss://evops/drone/D02/shot-01.png");
        req.setAnnotatedBy("巡检员-王五");
        DefectAnnotation annotation = defectManageService.annotate(defect.getId(), req);

        assertNotNull(annotation.getId());
        assertEquals(defect.getId(), annotation.getDefectId());
        assertEquals(defect.getOrderId(), annotation.getOrderId());
        // 未指定影像地址时取工单登记的无人机影像
        assertEquals("oss://evops/drone/D02/full.jpg", annotation.getImageUrl());
        assertEquals("距叶尖 2.3m 接闪器处", annotation.getPositionDesc());
        assertEquals("烧蚀斑直径 45mm", annotation.getSizeDesc());
        assertEquals("oss://evops/drone/D02/shot-01.png", annotation.getScreenshotUrl());
        assertEquals("巡检员-王五", annotation.getAnnotatedBy());
    }

    @Test
    void 专家复核_升级降级不覆盖初判并重生成建议() {
        Defect defect = registerDefect("D03", DefectType.ICING, DefectLevel.MINOR, "前缘结冰带");
        assertEquals(DefectLevel.MINOR, defect.getInitialLevel());

        // 升级：MINOR -> CRITICAL
        ReviewRequest upgrade = new ReviewRequest();
        upgrade.setReviewer("专家-赵六");
        upgrade.setToLevel(DefectLevel.CRITICAL);
        upgrade.setOpinion("结冰带覆盖前缘 1/3，存在质量不平衡风险");
        upgrade.setResponsiblePerson("值长-钱七");
        defect = defectManageService.review(defect.getId(), upgrade);

        assertEquals(DefectLevel.CRITICAL, defect.getDefectLevel());
        // 初判等级不被覆盖
        assertEquals(DefectLevel.MINOR, defect.getInitialLevel());
        // 等级变化重新生成维修建议
        assertEquals("结冰危急：立即停机，24小时内完成除冰并检查叶片质量不平衡",
                defect.getRepairAdvice());
        // 记录责任人
        assertEquals("值长-钱七", defect.getResponsiblePerson());
        // 处置时限按新等级从登记时刻重算（24h）
        assertTrue(defect.getDisposeDeadline().isBefore(LocalDateTime.now().plusHours(25)));

        // 降级：CRITICAL -> GENERAL
        ReviewRequest downgrade = new ReviewRequest();
        downgrade.setReviewer("专家-赵六");
        downgrade.setToLevel(DefectLevel.GENERAL);
        downgrade.setOpinion("现场复测结冰已自然消融，降为一般");
        downgrade.setResponsiblePerson("班长-孙八");
        defect = defectManageService.review(defect.getId(), downgrade);

        assertEquals(DefectLevel.GENERAL, defect.getDefectLevel());
        assertEquals(DefectLevel.MINOR, defect.getInitialLevel());
        assertEquals("结冰一般：结冰期降功率运行，评估启动除冰加热", defect.getRepairAdvice());
        assertEquals("班长-孙八", defect.getResponsiblePerson());

        // 复核记录留痕：两次
        Map<String, Object> detail = defectManageService.detail(defect.getId());
        @SuppressWarnings("unchecked")
        List<com.evops.entity.DefectReview> reviews =
                (List<com.evops.entity.DefectReview>) detail.get("reviews");
        assertEquals(2, reviews.size());
        assertEquals("MINOR", reviews.get(0).getFromLevel());
        assertEquals("CRITICAL", reviews.get(0).getToLevel());
        assertEquals("CRITICAL", reviews.get(1).getFromLevel());
        assertEquals("GENERAL", reviews.get(1).getToLevel());
    }

    @Test
    void 专家复核_同级拒绝_已关闭只能追加说明() {
        Defect defect = registerDefect("D04", DefectType.COATING_PEELING, DefectLevel.GENERAL, "前缘涂层剥落");

        // 同级复核不构成升降级，拒绝
        ReviewRequest same = new ReviewRequest();
        same.setReviewer("专家-赵六");
        same.setToLevel(DefectLevel.GENERAL);
        same.setResponsiblePerson("值长-钱七");
        Long defectId = defect.getId();
        assertThrows(IllegalArgumentException.class,
                () -> defectManageService.review(defectId, same));

        // 走完维修验收，缺陷关闭
        RepairRequest repair = new RepairRequest();
        repair.setRepairResult("表面清理后前缘防护涂层修补");
        inspectionService.repair(defectId, repair);
        AcceptRequest accept = new AcceptRequest();
        accept.setPass(true);
        accept.setRemark("复检合格");
        defect = inspectionService.accept(defectId, accept);
        assertEquals(DefectStatus.ACCEPTED, defect.getStatus());
        assertEquals(OrderStatus.ACCEPTED,
                inspectionService.mustGetOrder(defect.getOrderId()).getStatus());

        // 已关闭：复核 / 标注 / 维修 全部拒绝
        ReviewRequest review = new ReviewRequest();
        review.setReviewer("专家-赵六");
        review.setToLevel(DefectLevel.SERIOUS);
        review.setResponsiblePerson("值长-钱七");
        assertThrows(IllegalArgumentException.class,
                () -> defectManageService.review(defectId, review));

        AnnotationRequest annotation = new AnnotationRequest();
        annotation.setPositionDesc("距叶根 8m 前缘");
        annotation.setSizeDesc("剥落 120mm × 30mm");
        annotation.setScreenshotUrl("oss://evops/drone/D04/shot.png");
        annotation.setAnnotatedBy("巡检员-王五");
        assertThrows(IllegalArgumentException.class,
                () -> defectManageService.annotate(defectId, annotation));

        RepairRequest repairAgain = new RepairRequest();
        repairAgain.setRepairResult("再次维修");
        assertThrows(IllegalArgumentException.class,
                () -> inspectionService.repair(defectId, repairAgain));

        // 已关闭唯一允许的写操作：追加说明
        NoteRequest note = new NoteRequest();
        note.setNote("关闭后补充：同批次叶片建议下周期重点复检前缘");
        note.setNotedBy("运检主管-周九");
        DefectNote saved = defectManageService.addNote(defectId, note);
        assertNotNull(saved.getId());

        Map<String, Object> detail = defectManageService.detail(defectId);
        @SuppressWarnings("unchecked")
        List<DefectNote> notes = (List<DefectNote>) detail.get("notes");
        assertEquals(1, notes.size());
        assertEquals("运检主管-周九", notes.get(0).getNotedBy());
    }

    @Test
    void 影像标注反查_定位分类原单处理人与异常原因() {
        Defect defect = registerDefect("D05", DefectType.CRACK, DefectLevel.GENERAL, "叶根区域可见裂纹");

        AnnotationRequest annotationReq = new AnnotationRequest();
        annotationReq.setPositionDesc("距叶根 1.2m 腹板");
        annotationReq.setSizeDesc("裂纹长 260mm × 宽 3mm");
        annotationReq.setScreenshotUrl("oss://evops/drone/D05/crack-01.png");
        annotationReq.setAnnotatedBy("巡检员-王五");
        DefectAnnotation annotation = defectManageService.annotate(defect.getId(), annotationReq);

        // 专家复核升级，记录责任人
        ReviewRequest review = new ReviewRequest();
        review.setReviewer("专家-赵六");
        review.setToLevel(DefectLevel.SERIOUS);
        review.setOpinion("裂纹位于叶根受力区，需停机评估");
        review.setResponsiblePerson("值长-钱七");
        defectManageService.review(defect.getId(), review);

        // 从标注反查
        AnnotationTraceDto trace = defectManageService.trace(annotation.getId());
        // 缺陷分类：初判 GENERAL，当前 SERIOUS
        assertEquals(DefectType.CRACK, trace.getDefectType());
        assertEquals(DefectLevel.GENERAL, trace.getInitialLevel());
        assertEquals(DefectLevel.SERIOUS, trace.getCurrentLevel());
        // 原单
        assertEquals(defect.getOrderId(), trace.getOrderId());
        InspectionOrder order = inspectionService.mustGetOrder(defect.getOrderId());
        assertEquals(order.getOrderNo(), trace.getOrderNo());
        assertEquals(order.getTurbineId(), trace.getTurbineId());
        // 处理人
        assertEquals("巡检员-王五", trace.getAnnotatedBy());
        assertEquals("专家-赵六", trace.getLatestReviewer());
        assertEquals("值长-钱七", trace.getResponsiblePerson());
        // 异常原因
        assertEquals("叶根区域可见裂纹", trace.getAnomalyReason());
        assertEquals("裂纹位于叶根受力区，需停机评估", trace.getLatestReviewOpinion());
    }

    @Test
    void 登记缺陷_未配置时限的类型等级组合被拒绝() {
        Turbine turbine = createTurbine("D06");
        Blade blade = bladeService.listInServiceAt(turbine.getId(),
                LocalDateTime.of(2026, 1, 1, 0, 0)).get(0);
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setTurbineId(turbine.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);
        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl("oss://evops/drone/D06/full.jpg");
        inspect.setInspectTime(LocalDateTime.now());
        inspectionService.submitInspection(order.getId(), inspect);

        // 清空某组合配置后登记应被拒绝
        DefectDisposeRule rule = defectManageService.mustGetRule(DefectType.LIGHTNING, DefectLevel.MINOR);
        defectManageService.removeById(rule.getId());

        DefectRegisterRequest defectReq = new DefectRegisterRequest();
        defectReq.setBladeId(blade.getId());
        defectReq.setDefectType(DefectType.LIGHTNING);
        defectReq.setDefectLevel(DefectLevel.MINOR);
        assertThrows(IllegalArgumentException.class,
                () -> inspectionService.registerDefect(order.getId(), defectReq));
    }
}
