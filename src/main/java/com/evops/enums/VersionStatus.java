package com.evops.enums;

/** 航线版本状态机：草稿→已发布→已退役（新版本发布时旧版本退役） */
public enum VersionStatus {
    DRAFT, PUBLISHED, RETIRED
}
