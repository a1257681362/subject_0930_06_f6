package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.BladePosition;
import com.evops.enums.BladeStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 叶片档案。归属窗口为 [installedAt, removedAt)，左闭右开，与风机服役窗口同一口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_blade")
public class Blade extends BaseEntity {
    /** 叶片编号，全局唯一（且不得与风机编号重复） */
    private String bladeNo;
    /** 出厂序列号 */
    private String serialNo;
    /** 方位：A/B/C 叶位 */
    private BladePosition bladePosition;
    /** 材料 */
    private String material;
    /** 所属风机 */
    private Long turbineId;
    /** 装上时刻（含） */
    private LocalDateTime installedAt;
    /** 拆下时刻（不含），null 表示在役 */
    private LocalDateTime removedAt;
    private BladeStatus status;
}
