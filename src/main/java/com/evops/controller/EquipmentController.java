package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.EquipmentRequest;
import com.evops.entity.Equipment;
import com.evops.service.EquipmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    /** 装备登记（可用窗口可空表示不限） */
    @PostMapping
    public ApiResponse<Equipment> register(@Validated @RequestBody EquipmentRequest req) {
        return ApiResponse.ok(equipmentService.register(req));
    }

    /** 装备列表 */
    @GetMapping
    public ApiResponse<List<Equipment>> list() {
        return ApiResponse.ok(equipmentService.listAll());
    }
}
