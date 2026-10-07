package com.evops.enums;

/** 飞行任务状态机：待起飞→已起飞→已完成；待起飞可取消 */
public enum TaskStatus {
    PLANNED, TAKEN_OFF, COMPLETED, CANCELLED
}
