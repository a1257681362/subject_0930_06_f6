package com.evops.enums;

/**
 * 结冰预警状态机：
 * ACTIVE 风险在控（已通知，未停机）
 *   -> STOP_CONFIRMED 人工确认停机建议并执行停机
 *   -> RESOLVED 再次确认解除风险。
 */
public enum IcingAlertStatus {
    ACTIVE, STOP_CONFIRMED, RESOLVED
}
