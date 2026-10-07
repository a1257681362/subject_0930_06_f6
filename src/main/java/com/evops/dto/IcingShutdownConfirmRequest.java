package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 停机人工确认：达到停机阈值只生成建议，确认后才执行停机 */
@Data
public class IcingShutdownConfirmRequest {
    @NotBlank(message = "确认人不能为空")
    private String confirmedBy;
    /** 值班通知回执编号（可选，便于关联通知与确认） */
    private String receiptNo;
    private String remark;
    /** 确认时刻，缺省取当前时刻 */
    private java.time.LocalDateTime confirmedAt;
}
