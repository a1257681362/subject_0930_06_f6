package com.evops.service;

import com.evops.common.GeoUtil;
import com.evops.dto.ConflictDto;
import com.evops.dto.WaypointDto;
import com.evops.entity.NoFlyZone;
import com.evops.enums.ZoneType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 发布前校验：临时/永久禁飞区、风速条件、电量余量。
 * 禁飞区冲突给出航点坐标和原因；风速/电量冲突给出限值与当前值。
 */
@Service
public class RouteCheckService {

    /** 电量安全余量：当前电量 - 航线预计耗电 不得低于该值（%） */
    public static final int MIN_BATTERY_RESERVE_PCT = 20;

    private final NoFlyZoneService noFlyZoneService;

    public RouteCheckService(NoFlyZoneService noFlyZoneService) {
        this.noFlyZoneService = noFlyZoneService;
    }

    /**
     * 校验航点串在当前条件下是否可发布。
     *
     * @param waypoints          航点串
     * @param maxWindSpeed       航线允许的最大风速（m/s），null 表示不限
     * @param requiredBatteryPct 航线预计耗电（%），null 表示不校验电量
     * @param windSpeed          当前风速（m/s），null 表示不校验风速
     * @param batteryPct         当前电量（%），null 表示不校验电量
     * @return 冲突列表，空表示通过
     */
    public List<ConflictDto> check(List<WaypointDto> waypoints, Double maxWindSpeed,
                                   Integer requiredBatteryPct, Double windSpeed, Integer batteryPct) {
        List<ConflictDto> conflicts = new ArrayList<>();

        // 禁飞区：逐航点核对当前生效区域
        List<NoFlyZone> zones = noFlyZoneService.listActiveAt(LocalDateTime.now());
        for (WaypointDto wp : waypoints) {
            for (NoFlyZone zone : zones) {
                // 航点高于限飞上限时不冲突；航点高度未知时按冲突处理（保守）
                if (zone.getMaxAltitudeM() != null && wp.getAltitudeM() != null
                        && wp.getAltitudeM() > zone.getMaxAltitudeM()) {
                    continue;
                }
                if (GeoUtil.pointInPolygon(wp.getLat(), wp.getLng(), zone.getPolygon())) {
                    ConflictDto c = new ConflictDto();
                    c.setType("NO_FLY_ZONE");
                    c.setWaypointSeq(wp.getSeq());
                    c.setLat(wp.getLat());
                    c.setLng(wp.getLng());
                    c.setZoneNo(zone.getZoneNo());
                    c.setZoneName(zone.getName());
                    c.setReason("航点" + wp.getSeq() + " (" + wp.getLat() + ", " + wp.getLng() + ") 位于"
                            + (zone.getZoneType() == ZoneType.TEMPORARY ? "临时" : "永久")
                            + "禁飞区「" + zone.getName() + "」内"
                            + (zone.getReason() != null ? "：" + zone.getReason() : ""));
                    conflicts.add(c);
                }
            }
        }

        // 风速条件
        if (windSpeed != null && maxWindSpeed != null && windSpeed > maxWindSpeed) {
            ConflictDto c = new ConflictDto();
            c.setType("WIND");
            c.setReason("风速超限：当前 " + windSpeed + " m/s，超过航线限值 " + maxWindSpeed + " m/s");
            conflicts.add(c);
        }

        // 电量余量
        if (batteryPct != null && requiredBatteryPct != null
                && batteryPct - requiredBatteryPct < MIN_BATTERY_RESERVE_PCT) {
            ConflictDto c = new ConflictDto();
            c.setType("BATTERY");
            c.setReason("电量余量不足：航线预计耗电 " + requiredBatteryPct + "%，安全余量需 ≥"
                    + MIN_BATTERY_RESERVE_PCT + "%，当前电量 " + batteryPct + "%");
            conflicts.add(c);
        }

        return conflicts;
    }
}
