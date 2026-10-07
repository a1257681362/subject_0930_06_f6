package com.evops.enums;

/** 班次状态机：候选→确认→作业中→已完成；落选候选被取代，未开工可取消 */
public enum ShiftStatus {
    CANDIDATE, CONFIRMED, IN_PROGRESS, COMPLETED, SUPERSEDED, CANCELLED
}
