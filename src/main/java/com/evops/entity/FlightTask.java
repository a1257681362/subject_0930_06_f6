package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.BladePosition;
import com.evops.enums.TaskStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 飞行任务：创建时绑定航线当前发布版本。
 * 新版本发布后，未起飞（PLANNED）任务改绑新版本；已起飞任务保持原版本，
 * 保证历史影像始终引用拍摄时的原航线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_flight_task")
public class FlightTask extends BaseEntity {
    /** 任务编号，全局唯一 */
    private String taskNo;
    private Long routeId;
    /** 绑定的航线版本；起飞后不再变化 */
    private Long routeVersionId;
    private Long turbineId;
    private BladePosition bladePosition;
    private LocalDateTime plannedTakeoffAt;
    private LocalDateTime takeoffAt;
    private LocalDateTime completedAt;
    private TaskStatus status;
}
