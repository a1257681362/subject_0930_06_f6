package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.dto.GeoPointDto;
import com.evops.enums.ZoneStatus;
import com.evops.enums.ZoneType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 禁飞区：多边形 + 限飞高度 + 生效窗口 [effectiveFrom, effectiveTo)，左闭右开。
 * 只取消不删除，历史区域保留用于追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_no_fly_zone")
public class NoFlyZone extends BaseEntity {
    /** 禁飞区编号，全局唯一 */
    private String zoneNo;
    private String name;
    /** 临时 / 永久 */
    private ZoneType zoneType;
    /** 多边形顶点 JSON：[{lat,lng}] */
    private String polygonJson;
    private Integer vertexCount;
    /** 限飞上限（米）：null 表示全高度禁飞；航点高度不超过该值即冲突 */
    private Double maxAltitudeM;
    /** 生效时刻（含） */
    private LocalDateTime effectiveFrom;
    /** 失效时刻（不含），null 表示长期有效 */
    private LocalDateTime effectiveTo;
    /** 划定原因（如军演临时管制） */
    private String reason;
    private ZoneStatus status;

    /** 非持久化：解析后的多边形顶点 */
    @TableField(exist = false)
    private List<GeoPointDto> polygon;
}
