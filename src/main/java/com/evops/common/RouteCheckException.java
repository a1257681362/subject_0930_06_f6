package com.evops.common;

import com.evops.dto.ConflictDto;

import java.util.List;

/**
 * 发布前校验未通过：携带冲突明细（含坐标和原因），
 * 由 GlobalExceptionHandler 转成 ApiResponse 返回调用方。
 */
public class RouteCheckException extends RuntimeException {

    private final List<ConflictDto> conflicts;

    public RouteCheckException(List<ConflictDto> conflicts) {
        super("发布前校验未通过，存在 " + conflicts.size() + " 项冲突");
        this.conflicts = conflicts;
    }

    public List<ConflictDto> getConflicts() {
        return conflicts;
    }
}
