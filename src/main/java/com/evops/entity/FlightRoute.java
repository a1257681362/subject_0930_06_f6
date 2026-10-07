package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.BladePosition;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡检航线：按 风机 + 叶片方位 + 拍摄距离 唯一配置。
 * 航线内容版本化（t_flight_route_version），currentVersionId 指向当前发布版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_flight_route")
public class FlightRoute extends BaseEntity {
    /** 航线编号，全局唯一 */
    private String routeNo;
    /** 所属风机 */
    private Long turbineId;
    /** 叶片方位：A/B/C 叶位 */
    private BladePosition bladePosition;
    /** 拍摄距离（米）：无人机距叶片的拍摄间隔 */
    private Double shootDistanceM;
    /** 航线名称 */
    private String name;
    /** 当前发布版本；null 表示尚未发布 */
    private Long currentVersionId;
}
