package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.AnnotationTraceDto;
import com.evops.service.DefectManageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 影像标注反查：定位缺陷分类、原单、处理人与异常原因 */
@RestController
@RequestMapping("/api/annotations")
public class AnnotationController {

    private final DefectManageService defectManageService;

    public AnnotationController(DefectManageService defectManageService) {
        this.defectManageService = defectManageService;
    }

    /** 反查：标注 → 缺陷分类 / 原单 / 处理人 / 异常原因 */
    @GetMapping("/{id}/trace")
    public ApiResponse<AnnotationTraceDto> trace(@PathVariable Long id) {
        return ApiResponse.ok(defectManageService.trace(id));
    }
}
