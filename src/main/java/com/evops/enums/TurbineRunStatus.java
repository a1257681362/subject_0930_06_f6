package com.evops.enums;

/**
 * 风电叶片运维对象（风机）有效标志。
 * 置为无效（INVALID）只改标志、不删除档案；页面、导出与后台查询按同一口径排除无效对象。
 */
public enum TurbineRunStatus {
    ACTIVE, INVALID
}
