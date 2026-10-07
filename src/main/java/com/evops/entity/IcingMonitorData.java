package com.evops.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.DataQualityFlag;
import com.evops.enums.IcingRiskLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 结冰监测数据：温度、湿度、风速、历史振动。
 * 同 风机+监测时刻 幂等（补传/补录/重试只更新该条并递增 revision），
 * 读数缺失或异常时 qualityFlag=DATA_MISSING，score/riskLevel/modelVersionId 留空，标记待补。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_monitor_data")
public class IcingMonitorData extends BaseEntity {
    private String dataNo;
    private Long turbineId;
    private LocalDateTime monitorTime;
    /** 环境温度（℃），缺失为 null；可空列更新时也要写入 null（补录/补传口径） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Double temperature;
    /** 相对湿度（%），缺失为 null */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Double humidityPct;
    /** 风速（m/s），缺失为 null */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Double windSpeed;
    /** 历史振动（g），缺失为 null */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Double vibration;

    private DataQualityFlag qualityFlag;
    /** 缺失/异常字段清单（逗号分隔），待补；补全后清空（null 也要写入） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String missingFields;

    /** 风险总分（0~100），待补数据为 null */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer score;
    /** 风险等级，待补数据为 null */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private IcingRiskLevel riskLevel;
    /** 评估时所用模型版本，待补数据为 null */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long modelVersionId;

    private Integer revision;
    private LocalDateTime evaluatedAt;
}
