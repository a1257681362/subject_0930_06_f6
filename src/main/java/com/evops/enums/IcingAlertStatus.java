package com.evops.enums;

/**
 * 结冰风险告警状态机：
 * ACTIVE 预警中（已发停机建议，等待人工确认停机）→
 * SHUTDOWN_CONFIRMED 已人工确认停机（等待人工确认解除）→
 * CLEARED 已人工确认解除（闭环）。
 */
public enum IcingAlertStatus {
    ACTIVE, SHUTDOWN_CONFIRMED, CLEARED
}
