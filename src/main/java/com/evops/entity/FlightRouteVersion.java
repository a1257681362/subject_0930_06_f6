package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.dto.WaypointDto;
import com.evops.enums.VersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 航线版本：航点串与作业限值的内容快照。
 * 新版本发布只作用于未起飞任务；已退役版本保留，供历史影像追溯引用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_flight_route_version")
public class FlightRouteVersion extends BaseEntity {
    private Long routeId;
    /** 版本号，航线内递增，从 1 开始 */
    private Integer versionNo;
    /** 航点串 JSON：[{seq,lat,lng,altitudeM,action}] */
    private String waypointsJson;
    private Integer waypointCount;
    /** 预计航程（米），按航点串球面距离求和 */
    private Double estimatedDistanceM;
    /** 预计耗电（%）：发布前校验电量余量的基准 */
    private Integer requiredBatteryPct;
    /** 允许作业的最大风速（m/s） */
    private Double maxWindSpeed;
    private VersionStatus status;
    /** 最近一次发布前校验结果（含冲突明细），留痕可追溯 */
    private String checkResultJson;
    private LocalDateTime publishedAt;

    /** 非持久化：解析后的航点串，用于接口出参 */
    @TableField(exist = false)
    private List<WaypointDto> waypoints;
}
