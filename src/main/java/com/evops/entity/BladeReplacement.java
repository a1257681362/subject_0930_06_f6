package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.BladePosition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 换件记录：保留旧叶片与新叶片的前后关系。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_blade_replacement")
public class BladeReplacement extends BaseEntity {
    private Long turbineId;
    private BladePosition bladePosition;
    /** 被换下的旧叶片 */
    private Long oldBladeId;
    /** 换上的新叶片 */
    private Long newBladeId;
    /** 换件时刻：旧叶片 removedAt 与新叶片 installedAt 同为该值 */
    private LocalDateTime replacedAt;
    private String reason;
}
