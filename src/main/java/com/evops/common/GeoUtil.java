package com.evops.common;

import com.evops.dto.GeoPointDto;
import com.evops.dto.WaypointDto;

import java.util.List;

/** 经纬度几何工具：射线法点在多边形内、哈弗辛球面距离 */
public final class GeoUtil {

    private static final double EARTH_RADIUS_M = 6371000d;

    private GeoUtil() {
    }

    /** 射线法判断点是否在多边形内（经纬度平面近似，场区尺度足够精确） */
    public static boolean pointInPolygon(double lat, double lng, List<GeoPointDto> polygon) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getLng();
            double yi = polygon.get(i).getLat();
            double xj = polygon.get(j).getLng();
            double yj = polygon.get(j).getLat();
            boolean intersect = (yi > lat) != (yj > lat)
                    && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi;
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 哈弗辛球面距离（米） */
    public static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /** 航点串总航程（米） */
    public static double totalDistanceM(List<WaypointDto> waypoints) {
        double total = 0d;
        for (int i = 1; i < waypoints.size(); i++) {
            WaypointDto prev = waypoints.get(i - 1);
            WaypointDto curr = waypoints.get(i);
            total += haversineM(prev.getLat(), prev.getLng(), curr.getLat(), curr.getLng());
        }
        return total;
    }
}
