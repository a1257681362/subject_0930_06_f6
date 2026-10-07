package com.evops.dto;

import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingRiskLevel;
import lombok.Data;

/** 叶片结冰风险预警查询条件；所有口径（页面/导出/后台）共用 */
@Data
public class IcingAlertFilter {
    private Long turbineId;
    private IcingAlertStatus status;
    private IcingRiskLevel riskLevel;
}
