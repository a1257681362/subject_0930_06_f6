package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.GeoPointDto;
import com.evops.dto.NoFlyZoneRequest;
import com.evops.entity.NoFlyZone;
import com.evops.enums.ZoneStatus;
import com.evops.mapper.NoFlyZoneMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 禁飞区：临时/永久，多边形 + 限飞高度 + 生效窗口 [effectiveFrom, effectiveTo)。
 * 只取消不删除，全部区域（含已取消）可查询，保证可追溯。
 */
@Service
public class NoFlyZoneService extends ServiceImpl<NoFlyZoneMapper, NoFlyZone> {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public NoFlyZoneService(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** 划设禁飞区 */
    public NoFlyZone create(NoFlyZoneRequest req) {
        if (req.getEffectiveTo() != null && !req.getEffectiveTo().isAfter(req.getEffectiveFrom())) {
            throw new IllegalArgumentException("失效时刻必须晚于生效时刻");
        }
        String zoneNo = StringUtils.hasText(req.getZoneNo())
                ? req.getZoneNo()
                : "NZ" + System.currentTimeMillis();
        Long dup = count(new QueryWrapper<NoFlyZone>().eq("zone_no", zoneNo));
        if (dup > 0) {
            throw new IllegalArgumentException("禁飞区编号已存在: " + zoneNo);
        }

        NoFlyZone zone = new NoFlyZone();
        zone.setZoneNo(zoneNo);
        zone.setName(req.getName());
        zone.setZoneType(req.getZoneType());
        zone.setPolygonJson(writeJson(req.getPolygon()));
        zone.setVertexCount(req.getPolygon().size());
        zone.setMaxAltitudeM(req.getMaxAltitudeM());
        zone.setEffectiveFrom(req.getEffectiveFrom());
        zone.setEffectiveTo(req.getEffectiveTo());
        zone.setReason(req.getReason());
        zone.setStatus(ZoneStatus.ACTIVE);
        save(zone);

        auditService.record("ZONE", zone.getId(), "ZONE_CREATE",
                "{\"zoneNo\":\"" + zoneNo + "\",\"zoneType\":\"" + req.getZoneType() + "\"}", null);
        zone.setPolygon(req.getPolygon());
        return zone;
    }

    /** 取消禁飞区：不删除，保留记录用于追溯 */
    public NoFlyZone cancel(Long id) {
        NoFlyZone zone = mustGet(id);
        if (zone.getStatus() != ZoneStatus.ACTIVE) {
            throw new IllegalArgumentException("禁飞区已取消: " + id);
        }
        zone.setStatus(ZoneStatus.CANCELLED);
        updateById(zone);
        auditService.record("ZONE", zone.getId(), "ZONE_CANCEL",
                "{\"zoneNo\":\"" + zone.getZoneNo() + "\"}", null);
        return zone;
    }

    /** 某时刻生效中的禁飞区：状态 ACTIVE 且 t ∈ [effectiveFrom, effectiveTo) */
    public List<NoFlyZone> listActiveAt(LocalDateTime t) {
        List<NoFlyZone> zones = list(new QueryWrapper<NoFlyZone>()
                .eq("status", ZoneStatus.ACTIVE)
                .le("effective_from", t)
                .and(w -> w.isNull("effective_to").or().gt("effective_to", t)));
        zones.forEach(this::attachPolygon);
        return zones;
    }

    /** 全量（含已取消、已过期），用于追溯 */
    public List<NoFlyZone> listAll() {
        List<NoFlyZone> zones = list(new QueryWrapper<NoFlyZone>().orderByAsc("id"));
        zones.forEach(this::attachPolygon);
        return zones;
    }

    public NoFlyZone mustGet(Long id) {
        NoFlyZone zone = getById(id);
        if (zone == null) {
            throw new IllegalArgumentException("禁飞区不存在: " + id);
        }
        attachPolygon(zone);
        return zone;
    }

    public void attachPolygon(NoFlyZone zone) {
        zone.setPolygon(readJson(zone.getPolygonJson(), new TypeReference<List<GeoPointDto>>() {
        }));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }
}
