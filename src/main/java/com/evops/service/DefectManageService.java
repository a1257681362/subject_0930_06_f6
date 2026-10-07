package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.AnnotationRequest;
import com.evops.dto.AnnotationTraceDto;
import com.evops.dto.DisposeRuleUpdateRequest;
import com.evops.dto.NoteRequest;
import com.evops.dto.ReviewRequest;
import com.evops.entity.Defect;
import com.evops.entity.DefectAnnotation;
import com.evops.entity.DefectDisposeRule;
import com.evops.entity.DefectNote;
import com.evops.entity.DefectReview;
import com.evops.entity.InspectionOrder;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import com.evops.mapper.DefectAnnotationMapper;
import com.evops.mapper.DefectDisposeRuleMapper;
import com.evops.mapper.DefectMapper;
import com.evops.mapper.DefectNoteMapper;
import com.evops.mapper.DefectReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 叶片缺陷管理：处置时限配置、影像标注、专家复核、追加说明与反查。
 *
 * 口径：
 * - 初判等级 initial_level 登记时确定，专家复核只调整当前等级，永不覆盖初判；
 * - 风险等级变化时按 类型+新等级 重新生成维修建议、重算处置时限（仍从登记时刻起算）并记录责任人；
 * - 已关闭（ACCEPTED）缺陷只读，唯一允许的写操作是追加说明。
 */
@Service
public class DefectManageService extends ServiceImpl<DefectDisposeRuleMapper, DefectDisposeRule> {

    private final InspectionService inspectionService;
    private final DefectMapper defectMapper;
    private final DefectAnnotationMapper annotationMapper;
    private final DefectReviewMapper reviewMapper;
    private final DefectNoteMapper noteMapper;
    private final AuditService auditService;

    public DefectManageService(InspectionService inspectionService, DefectMapper defectMapper,
                               DefectAnnotationMapper annotationMapper, DefectReviewMapper reviewMapper,
                               DefectNoteMapper noteMapper, AuditService auditService) {
        this.inspectionService = inspectionService;
        this.defectMapper = defectMapper;
        this.annotationMapper = annotationMapper;
        this.reviewMapper = reviewMapper;
        this.noteMapper = noteMapper;
        this.auditService = auditService;
    }

    /** 处置时限配置列表 */
    public List<DefectDisposeRule> listRules() {
        return list(new QueryWrapper<DefectDisposeRule>()
                .orderByAsc("defect_type").orderByAsc("dispose_hours"));
    }

    /** 调整处置时限与维修建议模板 */
    @Transactional
    public DefectDisposeRule updateRule(Long ruleId, DisposeRuleUpdateRequest req) {
        DefectDisposeRule rule = getById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("处置时限配置不存在: " + ruleId);
        }
        rule.setDisposeHours(req.getDisposeHours());
        rule.setAdviceTemplate(req.getAdviceTemplate());
        updateById(rule);
        auditService.record("DEFECT_DISPOSE_RULE", ruleId, "UPDATE_RULE",
                "处置时限调整为 " + req.getDisposeHours() + " 小时", null);
        return rule;
    }

    /** 按 类型+等级 取配置 */
    public DefectDisposeRule mustGetRule(DefectType type, DefectLevel level) {
        DefectDisposeRule rule = getOne(new QueryWrapper<DefectDisposeRule>()
                .eq("defect_type", type)
                .eq("defect_level", level));
        if (rule == null) {
            throw new IllegalArgumentException("未配置处置时限: " + type + "/" + level);
        }
        return rule;
    }

    /** 影像标注：巡检员标注位置、尺寸与截图；已关闭缺陷不允许标注 */
    public DefectAnnotation annotate(Long defectId, AnnotationRequest req) {
        Defect defect = inspectionService.mustGetDefect(defectId);
        if (defect.getStatus() == DefectStatus.ACCEPTED) {
            throw new IllegalArgumentException("缺陷已关闭，只能追加说明: " + defectId);
        }
        InspectionOrder order = inspectionService.mustGetOrder(defect.getOrderId());

        DefectAnnotation annotation = new DefectAnnotation();
        annotation.setDefectId(defectId);
        annotation.setOrderId(order.getId());
        annotation.setImageUrl(StringUtils.hasText(req.getImageUrl())
                ? req.getImageUrl() : order.getDroneImageUrl());
        annotation.setPositionDesc(req.getPositionDesc());
        annotation.setSizeDesc(req.getSizeDesc());
        annotation.setScreenshotUrl(req.getScreenshotUrl());
        annotation.setAnnotatedBy(req.getAnnotatedBy());
        annotation.setAnnotatedAt(LocalDateTime.now());
        annotationMapper.insert(annotation);
        return annotation;
    }

    /**
     * 专家复核：可升级或降级，但不覆盖初判。
     * 等级变化后重新生成维修建议、重算处置时限（仍从登记时刻起算）并记录责任人。
     */
    @Transactional
    public Defect review(Long defectId, ReviewRequest req) {
        Defect defect = inspectionService.mustGetDefect(defectId);
        if (defect.getStatus() != DefectStatus.OPEN) {
            throw new IllegalArgumentException("仅待维修缺陷可复核: " + defect.getStatus());
        }
        DefectLevel from = defect.getDefectLevel();
        DefectLevel to = req.getToLevel();
        if (from == to) {
            throw new IllegalArgumentException("复核等级与当前等级相同，不构成升级或降级: " + to);
        }

        // 初判等级 initialLevel 保持不变，仅调整当前等级
        DefectDisposeRule rule = mustGetRule(defect.getDefectType(), to);
        defect.setDefectLevel(to);
        defect.setRepairAdvice(rule.getAdviceTemplate());
        LocalDateTime base = defect.getCreateTime() != null ? defect.getCreateTime() : LocalDateTime.now();
        defect.setDisposeDeadline(base.plusHours(rule.getDisposeHours()));
        defect.setResponsiblePerson(req.getResponsiblePerson());
        defectMapper.updateById(defect);

        DefectReview review = new DefectReview();
        review.setDefectId(defectId);
        review.setReviewer(req.getReviewer());
        review.setFromLevel(from.name());
        review.setToLevel(to.name());
        review.setOpinion(req.getOpinion());
        review.setResponsiblePerson(req.getResponsiblePerson());
        review.setReviewedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        auditService.record("DEFECT", defectId, "REVIEW",
                "专家复核 " + from + " -> " + to + "，重新生成维修建议，责任人: "
                        + req.getResponsiblePerson(),
                req.getReviewer());
        return defect;
    }

    /** 追加说明：任何状态可追加；已关闭缺陷唯一允许的写操作 */
    public DefectNote addNote(Long defectId, NoteRequest req) {
        inspectionService.mustGetDefect(defectId);
        DefectNote note = new DefectNote();
        note.setDefectId(defectId);
        note.setNote(req.getNote());
        note.setNotedBy(req.getNotedBy());
        note.setNotedAt(LocalDateTime.now());
        noteMapper.insert(note);
        return note;
    }

    /** 缺陷详情：登记信息 + 标注 + 复核记录 + 追加说明 */
    public Map<String, Object> detail(Long defectId) {
        Map<String, Object> result = new HashMap<>();
        result.put("defect", inspectionService.mustGetDefect(defectId));
        result.put("annotations", annotationMapper.selectList(new QueryWrapper<DefectAnnotation>()
                .eq("defect_id", defectId).orderByAsc("id")));
        result.put("reviews", reviewMapper.selectList(new QueryWrapper<DefectReview>()
                .eq("defect_id", defectId).orderByAsc("id")));
        result.put("notes", noteMapper.selectList(new QueryWrapper<DefectNote>()
                .eq("defect_id", defectId).orderByAsc("id")));
        return result;
    }

    /** 反查：从影像标注定位缺陷分类、原单、处理人与异常原因 */
    public AnnotationTraceDto trace(Long annotationId) {
        DefectAnnotation annotation = annotationMapper.selectById(annotationId);
        if (annotation == null) {
            throw new IllegalArgumentException("影像标注不存在: " + annotationId);
        }
        Defect defect = inspectionService.mustGetDefect(annotation.getDefectId());
        InspectionOrder order = inspectionService.mustGetOrder(defect.getOrderId());
        DefectReview latestReview = reviewMapper.selectOne(new QueryWrapper<DefectReview>()
                .eq("defect_id", defect.getId())
                .orderByDesc("id")
                .last("LIMIT 1"));

        AnnotationTraceDto dto = new AnnotationTraceDto();
        dto.setAnnotation(annotation);
        dto.setDefectId(defect.getId());
        dto.setDefectType(defect.getDefectType());
        dto.setInitialLevel(defect.getInitialLevel());
        dto.setCurrentLevel(defect.getDefectLevel());
        dto.setDefectStatus(defect.getStatus());
        dto.setAnomalyReason(defect.getDescription());
        dto.setLatestReviewOpinion(latestReview != null ? latestReview.getOpinion() : null);
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setTurbineId(order.getTurbineId());
        dto.setAnnotatedBy(annotation.getAnnotatedBy());
        dto.setLatestReviewer(latestReview != null ? latestReview.getReviewer() : null);
        dto.setResponsiblePerson(defect.getResponsiblePerson());
        return dto;
    }
}
