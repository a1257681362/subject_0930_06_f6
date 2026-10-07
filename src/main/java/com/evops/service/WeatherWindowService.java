package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.WeatherWindowRequest;
import com.evops.entity.WeatherWindow;
import com.evops.entity.WorkShift;
import com.evops.enums.LightningRisk;
import com.evops.enums.NotifyType;
import com.evops.enums.ShiftStatus;
import com.evops.mapper.WeatherWindowMapper;
import com.evops.mapper.WorkShiftMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 天气窗口：读取风速/降雨/雷电窗口，支撑高空作业排班。
 *
 * 口径：
 * - 同一风机的重叠窗口为同一逻辑窗口：补传/补录/重试归并更新（起止与读数以最新
 *   上报为准，revision 递增），只形成一条可用结果；
 * - 适窗条件：最大风速 ≤ 10.0 m/s、无降雨、雷电风险 NONE；
 * - 窗口收窄（或条件恶化）导致已排班次不再适窗时自动预警，但系统不静默改期，
 *   改期只能走班次显式延期。
 */
@Service
public class WeatherWindowService extends ServiceImpl<WeatherWindowMapper, WeatherWindow> {

    /** 高空作业风速限值 m/s */
    public static final double MAX_WIND_SPEED = 10.0;
    /** 高空作业降雨量上限 mm：有降雨即不适窗 */
    public static final double MAX_RAINFALL_MM = 0.0;
    /** 自动预警通知对象 */
    public static final String DUTY_DISPATCHER = "值班调度";

    private final WorkShiftMapper workShiftMapper;
    private final ShiftNotificationService notificationService;
    private final AuditService auditService;

    public WeatherWindowService(WorkShiftMapper workShiftMapper,
                                ShiftNotificationService notificationService,
                                AuditService auditService) {
        this.workShiftMapper = workShiftMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /** 适窗条件：风速/降雨/雷电同时满足 */
    public static boolean suitableConditions(double windSpeed, double rainfallMm, LightningRisk lightningRisk) {
        return windSpeed <= MAX_WIND_SPEED
                && rainfallMm <= MAX_RAINFALL_MM
                && lightningRisk == LightningRisk.NONE;
    }

    /** 窗口是否适窗 */
    public static boolean isSuitable(WeatherWindow window) {
        return suitableConditions(window.getMaxWindSpeed(), window.getRainfallMm(), window.getLightningRisk());
    }

    /** 班次时段是否落在适窗内（窗口左闭右开） */
    public static boolean fits(WeatherWindow window, LocalDateTime start, LocalDateTime end) {
        return !start.isBefore(window.getWindowStart())
                && !end.isAfter(window.getWindowEnd())
                && isSuitable(window);
    }

    /**
     * 上报天气窗口：同一风机的重叠窗口归并为一条（补传/补录/重试幂等）。
     * 归并时窗口起止以最新上报为准（收窄/放宽都体现为同一窗口的更新），
     * 归并后重新评估受影响的已排班次，不再适窗的自动预警，但不改动班次。
     */
    @Transactional
    public WeatherWindow report(WeatherWindowRequest req) {
        if (!req.getWindowEnd().isAfter(req.getWindowStart())) {
            throw new IllegalArgumentException("窗口结束时刻必须晚于开始时刻");
        }
        // 重叠即同一逻辑窗口：[start, end) 与既有窗口有交集则归并
        WeatherWindow existing = getOne(new QueryWrapper<WeatherWindow>()
                .eq("turbine_id", req.getTurbineId())
                .and(w -> w.lt("window_start", req.getWindowEnd())
                        .gt("window_end", req.getWindowStart()))
                .orderByAsc("window_start")
                .last("LIMIT 1"));
        if (existing == null) {
            String windowNo = StringUtils.hasText(req.getWindowNo())
                    ? req.getWindowNo()
                    : "WW" + System.currentTimeMillis();
            Long dup = count(new QueryWrapper<WeatherWindow>().eq("window_no", windowNo));
            if (dup > 0) {
                throw new IllegalArgumentException("窗口编号已存在: " + windowNo);
            }
            WeatherWindow window = new WeatherWindow();
            window.setWindowNo(windowNo);
            window.setTurbineId(req.getTurbineId());
            window.setWindowStart(req.getWindowStart());
            window.setWindowEnd(req.getWindowEnd());
            window.setMaxWindSpeed(req.getMaxWindSpeed());
            window.setRainfallMm(req.getRainfallMm());
            window.setLightningRisk(req.getLightningRisk());
            window.setSource(req.getSource());
            window.setRevision(1);
            save(window);
            auditService.record("WEATHER_WINDOW", window.getId(), "WINDOW_REPORT",
                    "{\"windowNo\":\"" + window.getWindowNo() + "\"}", null);
            return window;
        }

        // 归并更新：窗口编号保持不变，起止与读数以最新上报为准，revision 递增
        existing.setWindowStart(req.getWindowStart());
        existing.setWindowEnd(req.getWindowEnd());
        existing.setMaxWindSpeed(req.getMaxWindSpeed());
        existing.setRainfallMm(req.getRainfallMm());
        existing.setLightningRisk(req.getLightningRisk());
        existing.setSource(req.getSource());
        existing.setRevision(existing.getRevision() + 1);
        updateById(existing);
        auditService.record("WEATHER_WINDOW", existing.getId(), "WINDOW_MERGE",
                "归并更新 revision=" + existing.getRevision(), null);
        warnUnfitShifts(existing);
        return existing;
    }

    /**
     * 窗口收窄/条件恶化预警：受影响的候选与已确认班次各发一条预警通知。
     * 只预警，不静默改期——班次时刻保持不变。
     */
    private void warnUnfitShifts(WeatherWindow window) {
        List<WorkShift> affected = workShiftMapper.selectList(new QueryWrapper<WorkShift>()
                .eq("window_id", window.getId())
                .in("status", Arrays.asList(ShiftStatus.CANDIDATE.name(), ShiftStatus.CONFIRMED.name())));
        for (WorkShift shift : affected) {
            if (fits(window, shift.getShiftStart(), shift.getShiftEnd())) {
                continue;
            }
            String content = "天气窗口变化，班次 " + shift.getShiftNo()
                    + " [" + shift.getShiftStart() + "~" + shift.getShiftEnd() + "] 已超出适窗范围"
                    + "（窗口 [" + window.getWindowStart() + "~" + window.getWindowEnd() + "]"
                    + " 风速" + window.getMaxWindSpeed() + "m/s"
                    + " 降雨" + window.getRainfallMm() + "mm"
                    + " 雷电" + window.getLightningRisk() + "），请评估后显式延期，系统不会自动改期";
            notificationService.create(shift.getId(), shift.getTaskId(),
                    NotifyType.WINDOW_NARROWED, DUTY_DISPATCHER, content);
            auditService.record("WORK_SHIFT", shift.getId(), "WINDOW_NARROWED_WARN", content, null);
        }
    }

    /** 风机窗口列表（按开始时刻升序） */
    public List<WeatherWindow> listByTurbine(Long turbineId) {
        return list(new QueryWrapper<WeatherWindow>()
                .eq(turbineId != null, "turbine_id", turbineId)
                .orderByAsc("window_start"));
    }

    public WeatherWindow mustGet(Long id) {
        WeatherWindow window = getById(id);
        if (window == null) {
            throw new IllegalArgumentException("天气窗口不存在: " + id);
        }
        return window;
    }
}
