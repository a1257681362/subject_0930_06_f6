package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 结冰监测数据上报入参（温度、湿度、风速、历史振动）。
 * 读数允许为空：缺失或异常的读数不参与评分，数据标记 DATA_MISSING 待补，补传后重算。
 * 同 风机+监测时刻 幂等：补传/补录/重试只更新该条并递增 revision。
 */
@Data
public class IcingMonitorRequest {
    /** 数据编号；为空时按 风机+时刻 自动生成，归并更新时保持不变 */
    private String dataNo;
    @NotNull(message = "风机不能为空")
    private Long turbineId;
    /** 监测时刻；为空取当前时刻 */
    private LocalDateTime monitorTime;
    /** 环境温度（℃），缺失/异常传 null */
    private Double temperature;
    /** 相对湿度（%），缺失/异常传 null */
    private Double humidityPct;
    /** 风速（m/s），缺失/异常传 null */
    private Double windSpeed;
    /** 历史振动（g），缺失/异常传 null */
    private Double vibration;
}
