package com.evops.enums;

/**
 * 备件状态机：在库 → 已领用 → 已安装；退库须经验收（RETURN_PENDING），
 * 验收通过回到在库，驳回回到已领用；旧件拆下后登记去向进入 RETIRED。
 * 已安装（INSTALLED）备件不能再次出库。
 */
public enum PartStatus {
    IN_STOCK, ISSUED, RETURN_PENDING, INSTALLED, RETIRED
}
