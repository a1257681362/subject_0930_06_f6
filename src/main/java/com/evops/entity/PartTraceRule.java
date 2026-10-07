package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.PartType;
import com.evops.enums.TraceLevel;
import com.evops.enums.VersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 序列追踪规则：按备件类型版本化。规则调整只生成新版本，
 * 旧版本退役但内容不改，已产生的追踪结论继续引用旧版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_part_trace_rule")
public class PartTraceRule extends BaseEntity {
    private PartType partType;
    /** 版本号，类型内递增 */
    private Integer versionNo;
    private TraceLevel traceLevel;
    /** 入库是否须核验 */
    private Boolean needInboundCheck;
    /** 库存期上限（月），空表示不限 */
    private Integer maxStockMonths;
    private String ruleContent;
    private VersionStatus status;
    private LocalDateTime publishedAt;
}
