package com.evops.enums;

/** 改线申请状态机：待处理→已批准/已驳回。两级审批只完成一半时保持 PENDING */
public enum ChangeStatus {
    PENDING, APPROVED, REJECTED
}
