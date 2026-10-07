package com.evops.enums;

/** 观测数据状态：正常 / 异常或缺失待补（补传前不参与风险计算、不生成告警） */
public enum IcingObservationStatus {
    OK, PENDING_SUPPLEMENT
}
