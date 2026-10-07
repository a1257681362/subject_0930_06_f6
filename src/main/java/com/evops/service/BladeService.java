package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.BladeRequest;
import com.evops.dto.ReplacementRequest;
import com.evops.entity.Blade;
import com.evops.entity.BladeReplacement;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.BladeStatus;
import com.evops.mapper.BladeMapper;
import com.evops.mapper.BladeReplacementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BladeService extends ServiceImpl<BladeMapper, Blade> {

    private final AssetNoGuard assetNoGuard;
    private final TurbineService turbineService;
    private final BladeReplacementMapper replacementMapper;

    public BladeService(AssetNoGuard assetNoGuard, TurbineService turbineService,
                        BladeReplacementMapper replacementMapper) {
        this.assetNoGuard = assetNoGuard;
        this.turbineService = turbineService;
        this.replacementMapper = replacementMapper;
    }

    /** 叶片建档：编号跨表唯一、序列号唯一、同风机同方位在役唯一 */
    public Blade addBlade(Long turbineId, BladeRequest req) {
        Turbine turbine = turbineService.mustGet(turbineId);
        assetNoGuard.assertBladeNoFree(req.getBladeNo());
        Long serialCount = count(new QueryWrapper<Blade>().eq("serial_no", req.getSerialNo()));
        if (serialCount > 0) {
            throw new IllegalArgumentException("叶片序列号已存在: " + req.getSerialNo());
        }
        Blade inService = findInService(turbineId, req.getBladePosition());
        if (inService != null) {
            throw new IllegalArgumentException("该方位已有在役叶片: " + req.getBladePosition()
                    + " -> " + inService.getBladeNo());
        }

        Blade blade = new Blade();
        blade.setBladeNo(req.getBladeNo());
        blade.setSerialNo(req.getSerialNo());
        blade.setBladePosition(req.getBladePosition());
        blade.setMaterial(req.getMaterial());
        blade.setTurbineId(turbineId);
        // 未指定装上时刻时默认取风机并网时间，与风机档案同一口径
        blade.setInstalledAt(req.getInstalledAt() != null ? req.getInstalledAt() : turbine.getGridConnectedAt());
        blade.setStatus(BladeStatus.IN_SERVICE);
        save(blade);
        return blade;
    }

    /**
     * 换件：旧叶片下架、新叶片上架、记录前后关系，同一事务完成。
     * 换件时刻同时是旧叶片归属窗口的止（不含）与新叶片归属窗口的起（含）。
     */
    @Transactional
    public BladeReplacement replace(Long turbineId, ReplacementRequest req) {
        turbineService.mustGet(turbineId);
        Blade oldBlade = findInService(turbineId, req.getBladePosition());
        if (oldBlade == null) {
            throw new IllegalArgumentException("该方位没有在役叶片可换: " + req.getBladePosition());
        }
        if (req.getReplacedAt().isBefore(oldBlade.getInstalledAt())) {
            throw new IllegalArgumentException("换件时刻早于旧叶片装上时刻: " + req.getReplacedAt());
        }
        if (!req.getNewBlade().getBladePosition().equals(req.getBladePosition())) {
            throw new IllegalArgumentException("新叶片方位与换件方位不一致");
        }

        // 旧叶片归属窗口闭合：[installedAt, replacedAt)
        oldBlade.setRemovedAt(req.getReplacedAt());
        oldBlade.setStatus(BladeStatus.REPLACED);
        updateById(oldBlade);

        // 新叶片归属窗口从换件时刻起算
        BladeRequest newReq = req.getNewBlade();
        newReq.setInstalledAt(req.getReplacedAt());
        Blade newBlade = addBlade(turbineId, newReq);

        // 保留前后关系
        BladeReplacement record = new BladeReplacement();
        record.setTurbineId(turbineId);
        record.setBladePosition(req.getBladePosition());
        record.setOldBladeId(oldBlade.getId());
        record.setNewBladeId(newBlade.getId());
        record.setReplacedAt(req.getReplacedAt());
        record.setReason(req.getReason());
        replacementMapper.insert(record);
        return record;
    }

    /** 当前在役叶片（某方位） */
    public Blade findInService(Long turbineId, BladePosition position) {
        return getOne(new QueryWrapper<Blade>()
                .eq("turbine_id", turbineId)
                .eq("blade_position", position)
                .isNull("removed_at")
                .eq("status", BladeStatus.IN_SERVICE), false);
    }

    /**
     * 归属口径：installed_at <= t < removed_at（左闭右开），与风机服役窗口同一口径。
     * 任意时刻同一方位至多一片在役叶片。
     */
    public List<Blade> listInServiceAt(Long turbineId, LocalDateTime t) {
        return list(new QueryWrapper<Blade>()
                .eq("turbine_id", turbineId)
                .le("installed_at", t)
                .and(w -> w.isNull("removed_at").or().gt("removed_at", t))
                .orderByAsc("blade_position"));
    }

    /** 叶片全量档案（含已换下），换件前后关系一并返回 */
    public List<Blade> listAll(Long turbineId) {
        return list(new QueryWrapper<Blade>()
                .eq("turbine_id", turbineId)
                .orderByAsc("blade_position", "installed_at"));
    }

    public List<BladeReplacement> listReplacements(Long turbineId) {
        return replacementMapper.selectList(new QueryWrapper<BladeReplacement>()
                .eq("turbine_id", turbineId)
                .orderByAsc("replaced_at"));
    }

    public Blade mustGet(Long id) {
        Blade blade = getById(id);
        if (blade == null) {
            throw new IllegalArgumentException("叶片不存在: " + id);
        }
        return blade;
    }
}
