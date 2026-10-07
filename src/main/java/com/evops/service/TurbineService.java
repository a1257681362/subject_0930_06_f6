package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.BladeRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.dto.TurbineUpdateRequest;
import com.evops.entity.Turbine;
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

    public TurbineService(AssetNoGuard assetNoGuard, @Lazy BladeService bladeService) {
        this.assetNoGuard = assetNoGuard;
        this.bladeService = bladeService;
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
        save(turbine);

        if (req.getBlades() != null) {
            for (BladeRequest blade : req.getBlades()) {
                bladeService.addBlade(turbine.getId(), blade);
            }
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
