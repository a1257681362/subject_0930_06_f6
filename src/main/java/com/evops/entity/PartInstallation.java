package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.PartDisposition;
import com.evops.enums.PartType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 装机台账（资产档案）。归属窗口 [installedAt, removedAt)，左闭右开，
 * 与叶片台账同一口径；拆下时登记旧件去向。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_part_installation")
public class PartInstallation extends BaseEntity {
    private Long partId;
    private Long requisitionId;
    private Long turbineId;
    private Long defectId;
    private PartType partType;
    /** 装机时刻（含） */
    private LocalDateTime installedAt;
    /** 拆下时刻（不含），null 表示在装 */
    private LocalDateTime removedAt;
    /** 旧件去向（拆下时登记） */
    private PartDisposition disposition;
    private String dispositionNote;
    /** 装机时所用规则版本 */
    private Long ruleVersionId;
    /** 序列追踪结论 */
    private String conclusion;
}
