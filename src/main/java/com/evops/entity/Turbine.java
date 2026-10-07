package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.TurbineStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 风机档案。服役窗口为 [gridConnectedAt, decommissionedAt)，左闭右开。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_turbine")
public class Turbine extends BaseEntity {
    /** 风机编号，全局唯一（且不得与叶片编号重复） */
    private String turbineNo;
    /** 场址 */
    private String site;
    /** 机型 */
    private String model;
    /** 并网时间 */
    private LocalDateTime gridConnectedAt;
    /** 退役时间，null 表示在运 */
    private LocalDateTime decommissionedAt;
    private TurbineStatus status;
}
