package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.entity.Blade;
import com.evops.entity.Turbine;
import com.evops.mapper.BladeMapper;
import com.evops.mapper.TurbineMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 编号唯一守卫：风机编号与叶片编号各自唯一，且两类编号之间也不得重复。
 */
@Component
public class AssetNoGuard {

    private final TurbineMapper turbineMapper;
    private final BladeMapper bladeMapper;

    public AssetNoGuard(TurbineMapper turbineMapper, BladeMapper bladeMapper) {
        this.turbineMapper = turbineMapper;
        this.bladeMapper = bladeMapper;
    }

    public void assertTurbineNoFree(String turbineNo) {
        if (!StringUtils.hasText(turbineNo)) {
            throw new IllegalArgumentException("风机编号不能为空");
        }
        if (turbineMapper.selectCount(new QueryWrapper<Turbine>().eq("turbine_no", turbineNo)) > 0) {
            throw new IllegalArgumentException("风机编号已存在: " + turbineNo);
        }
        if (bladeMapper.selectCount(new QueryWrapper<Blade>().eq("blade_no", turbineNo)) > 0) {
            throw new IllegalArgumentException("编号与叶片编号重复: " + turbineNo);
        }
    }

    public void assertBladeNoFree(String bladeNo) {
        if (!StringUtils.hasText(bladeNo)) {
            throw new IllegalArgumentException("叶片编号不能为空");
        }
        if (bladeMapper.selectCount(new QueryWrapper<Blade>().eq("blade_no", bladeNo)) > 0) {
            throw new IllegalArgumentException("叶片编号已存在: " + bladeNo);
        }
        if (turbineMapper.selectCount(new QueryWrapper<Turbine>().eq("turbine_no", bladeNo)) > 0) {
            throw new IllegalArgumentException("编号与风机编号重复: " + bladeNo);
        }
    }
}
