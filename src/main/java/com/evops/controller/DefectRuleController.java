package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.DisposeRuleUpdateRequest;
import com.evops.entity.DefectDisposeRule;
import com.evops.service.DefectManageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 缺陷处置时限配置：按 缺陷类型 × 等级 配置处置时限与维修建议模板 */
@RestController
@RequestMapping("/api/defect-rules")
public class DefectRuleController {

    private final DefectManageService defectManageService;

    public DefectRuleController(DefectManageService defectManageService) {
        this.defectManageService = defectManageService;
    }

    /** 处置时限配置列表 */
    @GetMapping
    public ApiResponse<List<DefectDisposeRule>> list() {
        return ApiResponse.ok(defectManageService.listRules());
    }

    /** 调整处置时限与维修建议模板 */
    @PutMapping("/{id}")
    public ApiResponse<DefectDisposeRule> update(@PathVariable Long id,
                                                 @Validated @RequestBody DisposeRuleUpdateRequest req) {
        return ApiResponse.ok(defectManageService.updateRule(id, req));
    }
}
