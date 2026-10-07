package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.BladeInvalidateRequest;
import com.evops.entity.Blade;
import com.evops.service.BladeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风电叶片运维对象：置为无效后不再参与叶片结冰风险预警。
 */
@RestController
@RequestMapping("/api/blades")
public class BladeController {

    private final BladeService bladeService;

    public BladeController(BladeService bladeService) {
        this.bladeService = bladeService;
    }

    /** 将叶片置为无效（报废/拆除停用）：预警页面/导出/后台同口径不再统计 */
    @PostMapping("/{id}/invalidate")
    public ApiResponse<Blade> invalidate(@PathVariable Long id,
                                         @Validated @RequestBody BladeInvalidateRequest req) {
        return ApiResponse.ok(bladeService.invalidate(id, req.getReason(), req.getOperator()));
    }
}
