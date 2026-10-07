package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.EquipmentRequest;
import com.evops.entity.Equipment;
import com.evops.enums.EquipmentStatus;
import com.evops.mapper.EquipmentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 装备：可用状态与可用窗口登记 */
@Service
public class EquipmentService extends ServiceImpl<EquipmentMapper, Equipment> {

    /** 装备登记：编号唯一，默认可用 */
    public Equipment register(EquipmentRequest req) {
        Long dup = count(new QueryWrapper<Equipment>().eq("equipment_no", req.getEquipmentNo()));
        if (dup > 0) {
            throw new IllegalArgumentException("装备编号已存在: " + req.getEquipmentNo());
        }
        Equipment equipment = new Equipment();
        equipment.setEquipmentNo(req.getEquipmentNo());
        equipment.setName(req.getName());
        equipment.setEquipmentType(req.getEquipmentType());
        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipment.setAvailableFrom(req.getAvailableFrom());
        equipment.setAvailableTo(req.getAvailableTo());
        save(equipment);
        return equipment;
    }

    public List<Equipment> listAll() {
        return list(new QueryWrapper<Equipment>().orderByAsc("id"));
    }

    public Equipment mustGet(Long id) {
        Equipment equipment = getById(id);
        if (equipment == null) {
            throw new IllegalArgumentException("装备不存在: " + id);
        }
        return equipment;
    }
}
