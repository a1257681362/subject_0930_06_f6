package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 影像标注入参：位置、尺寸与截图 */
@Data
public class AnnotationRequest {
    /** 影像地址；为空时取工单登记的无人机影像 */
    private String imageUrl;
    @NotBlank(message = "标注位置不能为空")
    private String positionDesc;
    @NotBlank(message = "标注尺寸不能为空")
    private String sizeDesc;
    @NotBlank(message = "标注截图不能为空")
    private String screenshotUrl;
    @NotBlank(message = "标注人不能为空")
    private String annotatedBy;
}
