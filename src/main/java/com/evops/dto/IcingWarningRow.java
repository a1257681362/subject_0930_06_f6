package com.evops.dto;

import com.evops.entity.IcingAlert;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 叶片结冰风险预警行：告警 + 风机/叶片编号 + 观测时刻。
 * 页面、导出与后台共用 {@code IcingAlertService.listWarningRows} 组装，保证同口径（仅有效叶片）。
 */
@Data
public class IcingWarningRow {
    private IcingAlert alert;
    private String turbineNo;
    private String bladeNo;
    private LocalDateTime observedAt;
}
