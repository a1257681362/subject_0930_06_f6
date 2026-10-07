package com.evops;

import com.evops.dto.BladeRequest;
import com.evops.dto.ReplacementRequest;
import com.evops.dto.TurbineCreateRequest;
import com.evops.dto.TurbineUpdateRequest;
import com.evops.entity.Blade;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.service.BladeService;
import com.evops.service.TurbineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临界时间归属口径验证：
 * 风机服役窗口 [grid_connected_at, decommissioned_at) 与
 * 叶片归属窗口 [installed_at, removed_at) 统一为左闭右开。
 * 把风机档案调到临界时间后，叶片档案必须按同一口径归属。
 */
@SpringBootTest
@Transactional
class BoundaryAttributionTest {

    /** 临界时间：日界零点 */
    private static final LocalDateTime BOUNDARY = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final LocalDateTime JUST_BEFORE = LocalDateTime.of(2025, 12, 31, 23, 59, 59);

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private BladeService bladeService;

    private BladeRequest blade(String no, String serial, BladePosition pos) {
        BladeRequest req = new BladeRequest();
        req.setBladeNo(no);
        req.setSerialNo(serial);
        req.setBladePosition(pos);
        req.setMaterial("碳纤维增强复合材料");
        return req;
    }

    private Turbine createAtBoundary() {
        TurbineCreateRequest req = new TurbineCreateRequest();
        req.setTurbineNo("BND-01");
        req.setSite("如东海上风电场");
        req.setModel("WT4000-D146");
        req.setGridConnectedAt(BOUNDARY);
        // 叶片不指定装上时刻 → 默认取并网时间，与风机档案同一口径
        req.setBlades(Arrays.asList(
                blade("BND-01-B1", "SN-BND-1", BladePosition.A),
                blade("BND-01-B2", "SN-BND-2", BladePosition.B),
                blade("BND-01-B3", "SN-BND-3", BladePosition.C)));
        return turbineService.create(req);
    }

    @Test
    void 风机与叶片在临界时刻同口径_左闭() {
        Turbine turbine = createAtBoundary();

        // 临界时刻（含）：风机在运，三片叶片同时归属
        assertTrue(turbineService.inServiceAt(turbine, BOUNDARY));
        List<Blade> atBoundary = bladeService.listInServiceAt(turbine.getId(), BOUNDARY);
        assertEquals(3, atBoundary.size());

        // 临界前一秒（不含）：风机未并网，叶片同样未归属 —— 同一口径
        assertFalse(turbineService.inServiceAt(turbine, JUST_BEFORE));
        assertEquals(0, bladeService.listInServiceAt(turbine.getId(), JUST_BEFORE).size());

        // 列表查询口径一致
        assertEquals(1, turbineService.listInServiceAt(BOUNDARY).size());
        assertEquals(0, turbineService.listInServiceAt(JUST_BEFORE).size());
    }

    @Test
    void 档案调到临界时间后_叶片按同一口径归属() {
        Turbine turbine = createAtBoundary();

        // 把风机档案调到另一个临界时间：2026-07-01 00:00:00
        LocalDateTime newBoundary = LocalDateTime.of(2026, 7, 1, 0, 0, 0);
        TurbineUpdateRequest adjust = new TurbineUpdateRequest();
        adjust.setGridConnectedAt(newBoundary);
        turbine = turbineService.updateArchive(turbine.getId(), adjust);

        // 风机服役窗口从新临界时刻起（左闭）
        assertTrue(turbineService.inServiceAt(turbine, newBoundary));
        assertFalse(turbineService.inServiceAt(turbine, newBoundary.minusSeconds(1)));

        // 叶片归属窗口仍是 [installed_at, removed_at)，同一左闭右开口径：
        // 叶片装于原并网时刻，新临界时刻之前已归属 —— 归属判断不因档案调整而改用别的口径
        List<Blade> beforeNewBoundary = bladeService.listInServiceAt(
                turbine.getId(), newBoundary.minusSeconds(1));
        assertEquals(3, beforeNewBoundary.size());
        assertEquals(3, bladeService.listInServiceAt(turbine.getId(), newBoundary).size());
    }

    @Test
    void 换件发生在临界时刻_新旧叶片归属交接同口径() {
        Turbine turbine = createAtBoundary();
        LocalDateTime swapAt = LocalDateTime.of(2026, 7, 1, 0, 0, 0);

        ReplacementRequest req = new ReplacementRequest();
        req.setBladePosition(BladePosition.A);
        req.setNewBlade(blade("BND-01-B1R", "SN-BND-1R", BladePosition.A));
        req.setReplacedAt(swapAt);
        req.setReason("到期更换");
        bladeService.replace(turbine.getId(), req);

        // 换件瞬间（临界时刻）：旧叶片窗口闭合（右开），新叶片窗口开启（左闭）
        List<Blade> at = bladeService.listInServiceAt(turbine.getId(), swapAt);
        assertEquals(3, at.size());
        assertTrue(at.stream().anyMatch(b -> "BND-01-B1R".equals(b.getBladeNo())));
        assertTrue(at.stream().noneMatch(b -> "BND-01-B1".equals(b.getBladeNo())));

        // 换件前一秒：仍是旧叶片归属
        List<Blade> before = bladeService.listInServiceAt(turbine.getId(), swapAt.minusSeconds(1));
        assertEquals(3, before.size());
        assertTrue(before.stream().anyMatch(b -> "BND-01-B1".equals(b.getBladeNo())));
        assertTrue(before.stream().noneMatch(b -> "BND-01-B1R".equals(b.getBladeNo())));

        // 任意时刻同一方位仅一片在役：窗口首尾相接、无重叠无空档
        Blade oldBlade = bladeService.listAll(turbine.getId()).stream()
                .filter(b -> "BND-01-B1".equals(b.getBladeNo())).findFirst().orElseThrow(AssertionError::new);
        Blade newBlade = bladeService.listAll(turbine.getId()).stream()
                .filter(b -> "BND-01-B1R".equals(b.getBladeNo())).findFirst().orElseThrow(AssertionError::new);
        assertEquals(swapAt, oldBlade.getRemovedAt());
        assertEquals(swapAt, newBlade.getInstalledAt());
    }

    @Test
    void 退役时刻右开_与叶片口径一致() {
        Turbine turbine = createAtBoundary();
        LocalDateTime retiredAt = LocalDateTime.of(2046, 1, 1, 0, 0, 0);

        TurbineUpdateRequest adjust = new TurbineUpdateRequest();
        adjust.setDecommissionedAt(retiredAt);
        turbine = turbineService.updateArchive(turbine.getId(), adjust);

        // 退役时刻（不含）：风机退出服役窗口 —— 与叶片 removed_at 的右开口径一致
        assertFalse(turbineService.inServiceAt(turbine, retiredAt));
        assertTrue(turbineService.inServiceAt(turbine, retiredAt.minusSeconds(1)));
    }
}
