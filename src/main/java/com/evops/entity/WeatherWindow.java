package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.LightningRisk;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 天气窗口：风速/降雨/雷电，窗口 [windowStart, windowEnd) 左闭右开。
 * 同一风机的重叠窗口为同一逻辑窗口：补传/补录/重试归并为一条记录并递增
 * revision（起止与读数以最新上报为准），只形成一条可用结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_weather_window")
public class WeatherWindow extends BaseEntity {
    /** 窗口编号，全局唯一；归并更新时保持首次编号不变 */
    private String windowNo;
    private Long turbineId;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    /** 窗口内最大风速 m/s */
    private Double maxWindSpeed;
    /** 窗口内降雨量 mm */
    private Double rainfallMm;
    private LightningRisk lightningRisk;
    /** 数据来源（气象台/人工补录等） */
    private String source;
    /** 版本：首次上报为 1，每次补传/补录 +1 */
    private Integer revision;
}
