package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingRiskLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 叶片结冰风险告警。
 * 产生时固化读数、得分、风险等级与模型版本（modelVersionId/versionNo 冗余落库）：
 * 历史告警不随模型参数调整重算。
 * 停机与解除风险均须人工确认（两次确认）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_icing_alert")
public class IcingAlert extends BaseEntity {
    private String alertNo;
    private Long turbineId;
    private Long monitorDataId;
    /** 产生告警时的模型版本（固化，历史告警不随新参数重算） */
    private Long modelVersionId;
    private Integer modelVersionNo;

    private LocalDateTime monitorTime;
    private Double temperature;
    private Double humidityPct;
    private Double windSpeed;
    private Double vibration;
    private Integer score;
    private IcingRiskLevel riskLevel;
    private String shutdownAdvice;

    private IcingAlertStatus status;
    private String shutdownConfirmedBy;
    private LocalDateTime shutdownConfirmedAt;
    private String clearConfirmedBy;
    private LocalDateTime clearConfirmedAt;
    private String closedRemark;
}
