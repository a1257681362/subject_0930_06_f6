package com.evops.enums;

/** 巡检工单状态机：待巡检→已巡检→已登记缺陷→已维修→已验收；无缺陷时直接 CLOSED */
public enum OrderStatus {
    PENDING, INSPECTED, DEFECT_REGISTERED, REPAIRED, ACCEPTED, CLOSED
}
