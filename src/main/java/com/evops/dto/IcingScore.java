package com.evops.dto;

import com.evops.enums.IcingRiskLevel;
import lombok.Data;

/** 结冰风险评分结果：总分、等级与四个因子分项得分 */
@Data
public class IcingScore {
    private int tempScore;
    private int humidityScore;
    private int windScore;
    private int vibrationScore;
    private int total;
    private IcingRiskLevel level;
}
