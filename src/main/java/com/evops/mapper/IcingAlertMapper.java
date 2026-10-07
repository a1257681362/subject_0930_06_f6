package com.evops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.dto.IcingAlertRow;
import com.evops.entity.IcingAlert;
import com.evops.enums.IcingAlertStatus;
import com.evops.enums.IcingRiskLevel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IcingAlertMapper extends BaseMapper<IcingAlert> {

    /**
     * 预警列表统一查询：告警联表风机，INNER JOIN t_turbine 且 run_status='ACTIVE'。
     * 已置为无效（INVALID）的运维对象一律不返回——页面、导出、后台共用此口径。
     */
    @Select("<script>"
            + "SELECT a.*, t.turbine_no AS turbine_no, t.site AS site "
            + "FROM t_icing_alert a "
            + "INNER JOIN t_turbine t ON t.id = a.turbine_id AND t.run_status = 'ACTIVE' "
            + "<where>"
            + "  <if test='turbineId != null'> AND a.turbine_id = #{turbineId}</if>"
            + "  <if test='status != null'> AND a.status = #{status}</if>"
            + "  <if test='riskLevel != null'> AND a.risk_level = #{riskLevel}</if>"
            + "  <if test='onlyOpen != null and onlyOpen'> AND a.status IN ('ACTIVE','SHUTDOWN_CONFIRMED')</if>"
            + "</where>"
            + "ORDER BY a.monitor_time DESC, a.id DESC"
            + "</script>")
    List<IcingAlertRow> selectRows(@Param("turbineId") Long turbineId,
                                   @Param("status") IcingAlertStatus status,
                                   @Param("riskLevel") IcingRiskLevel riskLevel,
                                   @Param("onlyOpen") Boolean onlyOpen);
}
