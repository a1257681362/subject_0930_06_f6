package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.entity.AuditEvent;
import com.evops.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 审计追溯：航线规划与禁飞区的动作留痕查询 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** 按对象追溯：entityType=ROUTE/VERSION/TASK/ZONE/CHANGE */
    @GetMapping
    public ApiResponse<List<AuditEvent>> listByEntity(@RequestParam String entityType,
                                                      @RequestParam Long entityId) {
        return ApiResponse.ok(auditService.listByEntity(entityType, entityId));
    }
}
