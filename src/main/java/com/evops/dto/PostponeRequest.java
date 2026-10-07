package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 延期入参：显式改期（区别于静默改期）。
 * 原计划由系统在首次延期时固化，通知回执随延期记录留存。
 */
@Data
public class PostponeRequest {
    @NotNull(message = "新开始时刻不能为空")
    private LocalDateTime newStart;
    @NotNull(message = "新结束时刻不能为空")
    private LocalDateTime newEnd;
    @NotNull(message = "延期原因不能为空")
    private String reason;
    @NotNull(message = "通知对象不能为空")
    private String recipient;
    @NotNull(message = "操作人不能为空")
    private String postponedBy;
}
