package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 缺陷登记：缺陷分类、初判等级（复核不覆盖）、当前等级、维修建议、
 * 责任人、处置截止时间、维修结果、验收结论。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect")
public class Defect extends BaseEntity {
    private Long orderId;
    private Long bladeId;
    /** 缺陷分类：裂纹/雷击/结冰/涂层剥落 */
    private DefectType defectType;
    /** 初判等级：巡检员登记时确定，专家复核可升/降当前等级但不覆盖初判 */
    private DefectLevel initialLevel;
    /** 当前缺陷等级（复核后可变） */
    private DefectLevel defectLevel;
    /** 维修建议：按 类型+当前等级 生成，等级变化时重新生成 */
    private String repairAdvice;
    /** 责任人：等级变化时记录 */
    private String responsiblePerson;
    /** 处置截止时间：登记时刻 + 类型×等级时限 */
    private LocalDateTime disposeDeadline;
    private String description;
    private DefectStatus status;
    /** 维修结果 */
    private String repairResult;
    private LocalDateTime repairedAt;
    private LocalDateTime acceptedAt;
    /** 验收备注（含驳回原因） */
    private String acceptRemark;
}
