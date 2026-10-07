package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.PartType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 入库单：一批一单。入库时按当前发布的序列追踪规则生成结论，
 * 结论引用规则版本，规则调整后旧结论不改动。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_part_inbound")
public class PartInbound extends BaseEntity {
    private String inboundNo;
    private String batchNo;
    private PartType partType;
    private Integer quantity;
    private String operator;
    private String remark;
    /** 入库校验所用规则版本 */
    private Long ruleVersionId;
    /** 序列追踪结论（如：按序列号追踪，入库校验通过） */
    private String conclusion;
    private LocalDateTime stockedAt;
}
