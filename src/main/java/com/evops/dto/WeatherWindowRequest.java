package com.evops.dto;

import com.evops.enums.LightningRisk;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 天气窗口上报入参。
 * 同一风机的重叠窗口为同一逻辑窗口：补传/补录/重试归并更新，只形成一条可用结果。
 */
@Data
public class WeatherWindowRequest {
    /** 窗口编号；为空时自动生成，归并更新时保持首次编号不变 */
    private String windowNo;
    @NotNull(message = "风机不能为空")
    private Long turbineId;
    @NotNull(message = "窗口开始时刻不能为空")
    private LocalDateTime windowStart;
    @NotNull(message = "窗口结束时刻不能为空")
    private LocalDateTime windowEnd;
    @NotNull(message = "最大风速不能为空")
    private Double maxWindSpeed;
    @NotNull(message = "降雨量不能为空")
    private Double rainfallMm;
    @NotNull(message = "雷电风险不能为空")
    private LightningRisk lightningRisk;
    private String source;
}
