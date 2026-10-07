package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 影像标注：巡检员在影像上标注位置、尺寸与截图。
 * 冗余 order_id 便于从标注反查原单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_annotation")
public class DefectAnnotation extends BaseEntity {
    private Long defectId;
    private Long orderId;
    /** 影像地址 */
    private String imageUrl;
    /** 标注位置描述（如：距叶根 12.5m 前缘） */
    private String positionDesc;
    /** 尺寸描述（如：长 380mm × 宽 12mm） */
    private String sizeDesc;
    /** 标注截图地址 */
    private String screenshotUrl;
    private String annotatedBy;
    private LocalDateTime annotatedAt;
}
