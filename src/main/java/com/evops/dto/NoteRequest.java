package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 追加说明入参：已关闭缺陷唯一允许的写操作 */
@Data
public class NoteRequest {
    @NotBlank(message = "说明内容不能为空")
    private String note;
    @NotBlank(message = "说明人不能为空")
    private String notedBy;
}
