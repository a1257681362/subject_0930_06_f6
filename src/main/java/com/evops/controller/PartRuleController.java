package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.TraceRuleAdjustRequest;
import com.evops.entity.PartTraceRule;
import com.evops.enums.PartType;
import com.evops.service.SparePartService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/part-rules")
public class PartRuleController {

    private final SparePartService sparePartService;

    public PartRuleController(SparePartService sparePartService) {
        this.sparePartService = sparePartService;
    }

    /** 规则全量版本（?partType= 过滤），含已退役旧版本 */
    @GetMapping
    public ApiResponse<List<PartTraceRule>> list(@RequestParam(required = false) PartType partType) {
        return ApiResponse.ok(sparePartService.listRules(partType));
    }

    /** 规则调整：只生成新版本，旧版本退役但内容不改，旧结论继续引用旧版本 */
    @PostMapping("/{partType}/versions")
    public ApiResponse<PartTraceRule> adjust(@PathVariable PartType partType,
                                             @Validated @RequestBody TraceRuleAdjustRequest req) {
        return ApiResponse.ok(sparePartService.adjustRule(partType, req));
    }
}
