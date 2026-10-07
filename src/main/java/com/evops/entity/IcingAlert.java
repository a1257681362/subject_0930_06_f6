package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingRiskLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 叶片结冰风险预警。
 * 风险分、分项与所用参数版本在告警时快照：历史告警不随新参数重算。
 * 状态机 ACTIVE -> STOP_CONFIRMED（人工确认停机） -> RESOLVED（再次确认解除）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_alert")
public class IcingAlert extends BaseEntity {
    private String alertNo;
    private Long turbineId;
    private Long bladeId;
    private Long observationId;
    private IcingRiskLevel riskLevel;
    private Integer riskScore;
    private Integer tempScore;
    private Integer humidityScore;
    private Integer windScore;
    private Integer vibrationScore;
    /** 停机建议（达到停机阈值时生成） */
    private String shutdownAdvice;
    /** 计算所用参数版本：固化引用，不随参数调整改变 */
    private Long paramVersionId;
    private Integer paramVersionNo;
    private IcingAlertStatus status;
    private String shutdownConfirmedBy;
    private LocalDateTime shutdownConfirmedAt;
    private String shutdownRemark;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolveRemark;
}
