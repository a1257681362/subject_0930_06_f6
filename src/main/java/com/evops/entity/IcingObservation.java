package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.IcingObservationStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 结冰观测数据：温度/湿度/风速必填，历史振动可缺失。
 * 任一必填项异常缺失（或振动历史不可得）时标记 PENDING_SUPPLEMENT，不参与风险计算；
 * 补传后重算，观测编号与首次观测时刻保留，revision 递增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_observation")
public class IcingObservation extends BaseEntity {
    private String observationNo;
    private Long turbineId;
    private Long bladeId;
    private LocalDateTime observedAt;
    private Double temperatureC;
    private Double humidityPct;
    private Double windSpeedMs;
    /** 历史振动：回看窗口内最大振动幅值（mm/s）；缺失为 null */
    private Double vibration;
    private IcingObservationStatus status;
    /** 缺失/异常字段名，逗号分隔 */
    private String missingFields;
    /** 首次上报时的参数版本：补传重算沿用同一版本 */
    private Long paramVersionId;
    private LocalDateTime supplementedAt;
    private Integer revision;
}
