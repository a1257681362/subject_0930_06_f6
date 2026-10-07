package com.evops.dto;

import com.evops.entity.DefectAnnotation;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import lombok.Data;

/**
 * 影像标注反查结果：从标注定位缺陷分类、原单、处理人与异常原因。
 */
@Data
public class AnnotationTraceDto {
    /** 标注本身 */
    private DefectAnnotation annotation;
    /** 缺陷分类 */
    private Long defectId;
    private DefectType defectType;
    /** 初判等级（复核不覆盖） */
    private DefectLevel initialLevel;
    /** 当前等级（复核后可能已升/降） */
    private DefectLevel currentLevel;
    private DefectStatus defectStatus;
    /** 异常原因：缺陷描述 + 最近复核意见 */
    private String anomalyReason;
    private String latestReviewOpinion;
    /** 原单 */
    private Long orderId;
    private String orderNo;
    private Long turbineId;
    /** 处理人 */
    private String annotatedBy;
    private String latestReviewer;
    private String responsiblePerson;
}
