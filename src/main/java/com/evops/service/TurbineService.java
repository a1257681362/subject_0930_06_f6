package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.BladeRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.dto.TurbineUpdateRequest;
import com.evops.entity.Turbine;
import com.evops.enums.TurbineRunStatus;
import com.evops.enums.TurbineStatus;
import com.evops.mapper.TurbineMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TurbineService extends ServiceImpl<TurbineMapper, Turbine> {

    private final AssetNoGuard assetNoGuard;
    private final BladeService bladeService;
    private final AuditService auditService;

    public TurbineService(AssetNoGuard assetNoGuard, @Lazy BladeService bladeService,
                          AuditService auditService) {
        this.assetNoGuard = assetNoGuard;
        this.bladeService = bladeService;
        this.auditService = auditService;
    }

    /** 建档：创建风机，可同时录入首批叶片 */
    @Transactional
    public Turbine create(TurbineCreateRequest req) {
        assetNoGuard.assertTurbineNoFree(req.getTurbineNo());

        Turbine turbine = new Turbine();
        turbine.setTurbineNo(req.getTurbineNo());
        turbine.setSite(req.getSite());
        turbine.setModel(req.getModel());
        turbine.setGridConnectedAt(req.getGridConnectedAt());
        turbine.setStatus(TurbineStatus.IN_SERVICE);
        turbine.setRunStatus(TurbineRunStatus.ACTIVE);
        save(turbine);

        if (req.getBlades() != null) {
            for (BladeRequest blade : req.getBlades()) {
                bladeService.addBlade(turbine.getId(), blade);
            }
        }
        return turbine;
    }

    /**
     * 将风电叶片运维对象（风机）置为无效：只置无效不删除档案。
     * 同时闭合服役窗口（decommissionedAt 取当前时刻）与左闭右开口径保持一致；重复置无效幂等。
     * 置无效后，叶片结冰风险预警的页面、导出与后台查询按同一口径排除该对象。
     */
    @Transactional
    public Turbine deactivate(Long id, String operator) {
        Turbine turbine = mustGet(id);
        turbine.setRunStatus(TurbineRunStatus.INVALID);
        turbine.setStatus(TurbineStatus.DECOMMISSIONED);
        if (turbine.getDecommissionedAt() == null) {
            turbine.setDecommissionedAt(LocalDateTime.now());
        }
        updateById(turbine);
        auditService.record("TURBINE", turbine.getId(), "DEACTIVATE",
                "风机 " + turbine.getTurbineNo() + " 置为无效，结冰预警页面/导出/后台同口径排除", operator);
        return turbine;
    }

    /** 取有效的运维对象；已置无效的风机拒绝参与结冰监测与预警 */
    public Turbine mustGetActive(Long id) {
        Turbine turbine = mustGet(id);
        if (turbine.getRunStatus() == TurbineRunStatus.INVALID) {
            throw new IllegalArgumentException("运维对象已置为无效，不参与叶片结冰预警: " + turbine.getTurbineNo());
        }
        return turbine;
    }

    /** 档案调整：如把并网时间调到临界时间 */
    public Turbine updateArchive(Long id, TurbineUpdateRequest req) {
        Turbine turbine = mustGet(id);
        if (req.getSite() != null) {
            turbine.setSite(req.getSite());
        }
        if (req.getModel() != null) {
            turbine.setModel(req.getModel());
        }
        if (req.getGridConnectedAt() != null) {
            turbine.setGridConnectedAt(req.getGridConnectedAt());
        }
        if (req.getDecommissionedAt() != null) {
            turbine.setDecommissionedAt(req.getDecommissionedAt());
        }
        if (req.getStatus() != null) {
            turbine.setStatus(req.getStatus());
        }
        updateById(turbine);
        return turbine;
    }

    public Turbine mustGet(Long id) {
        Turbine turbine = getById(id);
        if (turbine == null) {
            throw new IllegalArgumentException("风机不存在: " + id);
        }
        return turbine;
    }

    /**
     * 服役窗口口径：grid_connected_at <= t < decommissioned_at（左闭右开）。
     * 与叶片归属窗口同一口径。
     */
    public boolean inServiceAt(Turbine turbine, LocalDateTime t) {
        if (turbine.getGridConnectedAt().isAfter(t)) {
            return false;
        }
        return turbine.getDecommissionedAt() == null || turbine.getDecommissionedAt().isAfter(t);
    }

    /** 查询某时刻在运的风机 */
    public List<Turbine> listInServiceAt(LocalDateTime t) {
        return list(new QueryWrapper<Turbine>()
                .le("grid_connected_at", t)
                .and(w -> w.isNull("decommissioned_at").or().gt("decommissioned_at", t)));
    }
}
