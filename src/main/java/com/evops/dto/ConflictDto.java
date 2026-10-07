package com.evops.dto;

import lombok.Data;

/**
 * 发布前校验冲突项：禁飞区冲突给出航点坐标与原因；
 * 风速/电量冲突无坐标（lat/lng 为 null），原因中给出限值与当前值。
 */
@Data
public class ConflictDto {
    /** 冲突类型：NO_FLY_ZONE / WIND / BATTERY */
    private String type;
    /** 冲突原因 */
    private String reason;
    /** 冲突航点序号（禁飞区冲突时有值） */
    private Integer waypointSeq;
    /** 冲突点坐标 */
    private Double lat;
    private Double lng;
    /** 命中的禁飞区 */
    private String zoneNo;
    private String zoneName;
}
