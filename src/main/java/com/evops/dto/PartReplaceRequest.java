package com.evops.dto;

import com.evops.enums.PartDisposition;
import lombok.Data;

import java.time.LocalDateTime;

/** 换件入参：替换后记录旧件去向与新件状态 */
@Data
public class PartReplaceRequest {
    /** 换件时刻，缺省取当前时刻 */
    private LocalDateTime replacedAt;
    /** 旧件去向；风机上存在同类型在装旧件时必填 */
    private PartDisposition oldDisposition;
    private String dispositionNote;
    /** 维修结果，缺省按换件内容自动生成 */
    private String repairResult;
    private String operator;
}
