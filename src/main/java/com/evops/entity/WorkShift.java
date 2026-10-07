package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.ShiftStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 班次：由适窗天气窗口 + 人员资质 + 装备可用性 + 维修时长生成。
 * 窗口收窄只预警不改期；延期走显式 postpone，
 * 首次延期把当前计划固化为 originalStart/originalEnd（原计划），之后不再改变。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_work_shift")
public class WorkShift extends BaseEntity {
    /** 班次编号，全局唯一 */
    private String shiftNo;
    private Long taskId;
    /** 生成所依据的天气窗口 */
    private Long windowId;
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;
    /** 原计划（首次延期时固化，不再改变） */
    private LocalDateTime originalStart;
    private LocalDateTime originalEnd;
    private Integer postponeCount;
    private ShiftStatus status;
}
