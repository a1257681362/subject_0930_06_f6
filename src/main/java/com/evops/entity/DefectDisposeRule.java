package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 处置时限配置：缺陷类型 + 等级 唯一。
 * 登记缺陷时按配置计算处置截止时间，并以 advice_template 生成初始维修建议。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_dispose_rule")
public class DefectDisposeRule extends BaseEntity {
    private String defectType;
    private String defectLevel;
    /** 处置时限（小时） */
    private Integer disposeHours;
    /** 维修建议模板 */
    private String adviceTemplate;
}
