package com.evops.dto;

import com.evops.enums.PartType;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** 备件入库入参：按序列号和批次入库 */
@Data
public class InboundRequest {
    /** 入库单号，缺省自动生成 */
    private String inboundNo;
    @NotBlank(message = "批次号不能为空")
    private String batchNo;
    @NotNull(message = "备件类型不能为空")
    private PartType partType;
    @NotBlank(message = "备件名称不能为空")
    private String name;
    private String spec;
    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String remark;
    /** 入库时刻，缺省取当前时刻 */
    private LocalDateTime stockedAt;
    @NotEmpty(message = "入库明细不能为空")
    @Valid
    private List<InboundItem> items;

    /** 入库明细：每件一个序列号 */
    @Data
    public static class InboundItem {
        @NotBlank(message = "序列号不能为空")
        private String serialNo;
        /** 备件编号，缺省取 P-序列号 */
        private String partNo;
    }
}
