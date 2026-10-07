package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 人员登记入参 */
@Data
public class WorkerRequest {
    @NotNull(message = "人员编号不能为空")
    private String workerNo;
    @NotNull(message = "姓名不能为空")
    private String name;
    @NotNull(message = "资质类型不能为空")
    private String qualificationType;
    @NotNull(message = "资质有效期不能为空")
    private LocalDateTime qualificationValidUntil;
}
