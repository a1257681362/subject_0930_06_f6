package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.AcceptRequest;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.OrderCreateRequest;
import com.evops.dto.RepairRequest;
import com.evops.entity.Blade;
import com.evops.entity.Defect;
import com.evops.entity.DefectDisposeRule;
import com.evops.entity.FlightTask;
import com.evops.entity.InspectionOrder;
import com.evops.enums.DefectStatus;
import com.evops.enums.OrderStatus;
import com.evops.enums.TaskStatus;
import com.evops.mapper.DefectDisposeRuleMapper;
import com.evops.mapper.DefectMapper;
import com.evops.mapper.InspectionOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InspectionService extends ServiceImpl<InspectionOrderMapper, InspectionOrder> {

    private final TurbineService turbineService;
    private final BladeService bladeService;
    private final DefectMapper defectMapper;
    private final DefectDisposeRuleMapper disposeRuleMapper;
    private final FlightTaskService flightTaskService;

    public InspectionService(TurbineService turbineService, BladeService bladeService,
                             DefectMapper defectMapper, DefectDisposeRuleMapper disposeRuleMapper,
                             FlightTaskService flightTaskService) {
        this.turbineService = turbineService;
        this.bladeService = bladeService;
        this.defectMapper = defectMapper;
        this.disposeRuleMapper = disposeRuleMapper;
        this.flightTaskService = flightTaskService;
    }

    /** 创建巡检工单 */
    public InspectionOrder createOrder(OrderCreateRequest req) {
        turbineService.mustGet(req.getTurbineId());
        if (req.getBladeId() != null) {
            Blade blade = bladeService.mustGet(req.getBladeId());
            if (!blade.getTurbineId().equals(req.getTurbineId())) {
                throw new IllegalArgumentException("叶片不属于该风机: " + req.getBladeId());
            }
        }
        String orderNo = StringUtils.hasText(req.getOrderNo())
                ? req.getOrderNo()
                : "IO" + System.currentTimeMillis();
        Long dup = count(new QueryWrapper<InspectionOrder>().eq("order_no", orderNo));
        if (dup > 0) {
            throw new IllegalArgumentException("工单号已存在: " + orderNo);
        }

        InspectionOrder order = new InspectionOrder();
        order.setOrderNo(orderNo);
        order.setTurbineId(req.getTurbineId());
        order.setBladeId(req.getBladeId());
        // 关联飞行任务：带出拍摄时绑定的航线版本，历史影像始终引用原航线版本
        if (req.getFlightTaskId() != null) {
            FlightTask task = flightTaskService.mustGet(req.getFlightTaskId());
            if (!task.getTurbineId().equals(req.getTurbineId())) {
                throw new IllegalArgumentException("飞行任务与工单风机不一致: " + req.getFlightTaskId());
            }
            if (task.getStatus() == TaskStatus.PLANNED || task.getStatus() == TaskStatus.CANCELLED) {
                throw new IllegalArgumentException("飞行任务尚未起飞，不能关联巡检工单: " + req.getFlightTaskId());
            }
            order.setFlightTaskId(task.getId());
            order.setRouteVersionId(task.getRouteVersionId());
        } else if (req.getRouteVersionId() != null) {
            order.setRouteVersionId(req.getRouteVersionId());
        }
        order.setStatus(OrderStatus.PENDING);
        save(order);
        return order;
    }

    /** 提交巡检结果：登记无人机影像与巡检时刻 */
    public InspectionOrder submitInspection(Long orderId, InspectSubmitRequest req) {
        InspectionOrder order = mustGetOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("工单状态不允许提交巡检结果: " + order.getStatus());
        }
        order.setDroneImageUrl(req.getDroneImageUrl());
        order.setInspectTime(req.getInspectTime());
        order.setStatus(OrderStatus.INSPECTED);
        updateById(order);
        return order;
    }

    /** 缺陷登记：一个工单可登记多条缺陷；按 类型+等级 配置生成处置时限与初始维修建议 */
    @Transactional
    public Defect registerDefect(Long orderId, DefectRegisterRequest req) {
        InspectionOrder order = mustGetOrder(orderId);
        if (order.getStatus() != OrderStatus.INSPECTED
                && order.getStatus() != OrderStatus.DEFECT_REGISTERED) {
            throw new IllegalArgumentException("工单状态不允许登记缺陷: " + order.getStatus());
        }
        Blade blade = bladeService.mustGet(req.getBladeId());
        if (!blade.getTurbineId().equals(order.getTurbineId())) {
            throw new IllegalArgumentException("缺陷叶片不属于工单风机: " + req.getBladeId());
        }

        Defect defect = new Defect();
        defect.setOrderId(orderId);
        defect.setBladeId(req.getBladeId());
        defect.setDefectType(req.getDefectType());
        // 登记等级即初判等级，专家复核可调整当前等级但不覆盖初判
        defect.setInitialLevel(req.getDefectLevel());
        defect.setDefectLevel(req.getDefectLevel());
        DefectDisposeRule rule = mustGetDisposeRule(req);
        defect.setRepairAdvice(rule.getAdviceTemplate());
        defect.setDisposeDeadline(LocalDateTime.now().plusHours(rule.getDisposeHours()));
        defect.setDescription(req.getDescription());
        defect.setStatus(DefectStatus.OPEN);
        defectMapper.insert(defect);

        order.setStatus(OrderStatus.DEFECT_REGISTERED);
        updateById(order);
        return defect;
    }

    private DefectDisposeRule mustGetDisposeRule(DefectRegisterRequest req) {
        DefectDisposeRule rule = disposeRuleMapper.selectOne(new QueryWrapper<DefectDisposeRule>()
                .eq("defect_type", req.getDefectType())
                .eq("defect_level", req.getDefectLevel()));
        if (rule == null) {
            throw new IllegalArgumentException(
                    "未配置处置时限: " + req.getDefectType() + "/" + req.getDefectLevel());
        }
        return rule;
    }

    /** 维修：回填维修结果 */
    @Transactional
    public Defect repair(Long defectId, RepairRequest req) {
        Defect defect = mustGetDefect(defectId);
        if (defect.getStatus() != DefectStatus.OPEN) {
            throw new IllegalArgumentException("缺陷状态不允许维修: " + defect.getStatus());
        }
        defect.setRepairResult(req.getRepairResult());
        defect.setRepairedAt(LocalDateTime.now());
        defect.setStatus(DefectStatus.REPAIRED);
        defectMapper.updateById(defect);

        InspectionOrder order = mustGetOrder(defect.getOrderId());
        order.setStatus(OrderStatus.REPAIRED);
        updateById(order);
        return defect;
    }

    /** 验收：通过则闭环；驳回则退回待维修。工单下全部缺陷验收通过后工单闭环 */
    @Transactional
    public Defect accept(Long defectId, AcceptRequest req) {
        Defect defect = mustGetDefect(defectId);
        if (defect.getStatus() != DefectStatus.REPAIRED) {
            throw new IllegalArgumentException("缺陷状态不允许验收: " + defect.getStatus());
        }
        InspectionOrder order = mustGetOrder(defect.getOrderId());

        if (Boolean.TRUE.equals(req.getPass())) {
            defect.setStatus(DefectStatus.ACCEPTED);
            defect.setAcceptedAt(LocalDateTime.now());
            defect.setAcceptRemark(req.getRemark());
            defectMapper.updateById(defect);

            Long remaining = defectMapper.selectCount(new QueryWrapper<Defect>()
                    .eq("order_id", order.getId())
                    .ne("status", DefectStatus.ACCEPTED));
            if (remaining == 0) {
                order.setStatus(OrderStatus.ACCEPTED);
                updateById(order);
            }
        } else {
            // 验收驳回：退回待维修
            defect.setStatus(DefectStatus.OPEN);
            defect.setAcceptRemark(req.getRemark());
            defectMapper.updateById(defect);

            order.setStatus(OrderStatus.DEFECT_REGISTERED);
            updateById(order);
        }
        return defect;
    }

    /** 无缺陷闭环 */
    public InspectionOrder close(Long orderId) {
        InspectionOrder order = mustGetOrder(orderId);
        if (order.getStatus() != OrderStatus.INSPECTED) {
            throw new IllegalArgumentException("工单状态不允许闭环: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CLOSED);
        updateById(order);
        return order;
    }

    public List<Defect> listDefects(Long orderId) {
        return defectMapper.selectList(new QueryWrapper<Defect>()
                .eq("order_id", orderId)
                .orderByAsc("id"));
    }

    public InspectionOrder mustGetOrder(Long id) {
        InspectionOrder order = getById(id);
        if (order == null) {
            throw new IllegalArgumentException("巡检工单不存在: " + id);
        }
        return order;
    }

    public Defect mustGetDefect(Long id) {
        Defect defect = defectMapper.selectById(id);
        if (defect == null) {
            throw new IllegalArgumentException("缺陷不存在: " + id);
        }
        return defect;
    }
}
