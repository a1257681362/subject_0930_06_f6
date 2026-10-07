package com.evops.dto;

import com.evops.entity.IcingAlert;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 结冰预警列表行：告警字段 + 所属风机编号/场址。
 * 由 t_icing_alert 与 t_turbine 联表产出，并统一排除 run_status=INVALID 的无效运维对象——
 * 页面、导出、后台三处共用同一查询与同一口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IcingAlertRow extends IcingAlert {
    private String turbineNo;
    private String site;
}
