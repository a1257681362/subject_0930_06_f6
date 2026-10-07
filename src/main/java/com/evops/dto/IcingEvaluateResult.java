package com.evops.dto;

import com.evops.entity.IcingAlert;
import com.evops.entity.IcingMonitorData;
import com.evops.entity.IcingModelVersion;
import lombok.Data;

/**
 * 单次监测数据上报的评估结果。
 * pendingSupplement=true 表示读数缺失/异常已标记待补，本次不评分、不告警；
 * alert 非空表示本次达到阈值并新生成了告警（同一轮结冰过程只生成一次）。
 */
@Data
public class IcingEvaluateResult {
    private IcingMonitorData monitorData;
    private IcingModelVersion modelVersion;
    private IcingScore score;
    private boolean pendingSupplement;
    /** 本次新生成的告警；持续超阈值的重复上报为 null（不重复告警、不重复通知） */
    private IcingAlert alert;
}
