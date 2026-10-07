package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.WorkerStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 人员：高空作业资质类型与有效期。
 * 资质有效期须覆盖整个班次时段，过期资质不能被排入任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_worker")
public class Worker extends BaseEntity {
    /** 人员编号，全局唯一 */
    private String workerNo;
    private String name;
    /** 资质类型，如 HIGH_ALTITUDE */
    private String qualificationType;
    /** 资质有效期（须不早于班次结束时刻） */
    private LocalDateTime qualificationValidUntil;
    private WorkerStatus status;
}
