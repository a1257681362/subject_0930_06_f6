package com.evops.dto;

import lombok.Data;

import java.util.List;

/** 发布前校验结果（试算） */
@Data
public class CheckResultDto {
    private boolean pass;
    private List<ConflictDto> conflicts;
}
