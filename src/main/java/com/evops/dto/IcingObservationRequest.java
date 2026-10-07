package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 结冰观测上报：温度/湿度/风速必填，历史振动可缺失（标记待补）。
 * 数据超出物理合理区间视为异常，同样标记待补，不参与风险计算。
 */
@Data
public class IcingObservationRequest {
    /** 观测记录编号，缺省自动生成 */
    private String observationNo;
    @NotNull(message = "叶片不能为空")
    private Long bladeId;
    /** 观测时刻，缺省取当前时刻 */
    private LocalDateTime observedAt;
    private Double temperatureC;
    private Double humidityPct;
    private Double windSpeedMs;
    /** 历史振动：回看窗口内最大振动幅值（mm/s），缺失可空 */
    private Double vibration;
    /** 上报人/值班人员 */
    private String operator;
}
