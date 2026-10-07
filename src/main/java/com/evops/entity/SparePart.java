package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.PartDisposition;
import com.evops.enums.PartStatus;
import com.evops.enums.PartType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 备件档案。按序列号和批次入库；已安装（INSTALLED）备件不能再次出库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_part")
public class SparePart extends BaseEntity {
    /** 备件编号，全局唯一 */
    private String partNo;
    /** 序列号，全局唯一 */
    private String serialNo;
    /** 入库批次号 */
    private String batchNo;
    /** 来源入库单 */
    private Long inboundId;
    /** 备件类型：叶片螺栓/传感器/涂层材料 */
    private PartType partType;
    private String name;
    private String spec;
    private PartStatus status;
    /** 入库时刻 */
    private LocalDateTime stockedAt;
    /** 旧件去向（拆下退役时登记） */
    private PartDisposition disposition;
    private LocalDateTime disposedAt;
}
