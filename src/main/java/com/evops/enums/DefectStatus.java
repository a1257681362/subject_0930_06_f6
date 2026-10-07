package com.evops.enums;

/** 缺陷状态机：待维修→已维修(待验收)→已验收；验收驳回回到 OPEN */
public enum DefectStatus {
    OPEN, REPAIRED, ACCEPTED
}
