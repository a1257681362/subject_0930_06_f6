package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.InboundRequest;
import com.evops.dto.IssueRequest;
import com.evops.dto.PartReplaceRequest;
import com.evops.dto.PartReturnRequest;
import com.evops.dto.PartTraceDto;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReturnAcceptRequest;
import com.evops.dto.TraceRuleAdjustRequest;
import com.evops.entity.Defect;
import com.evops.entity.InspectionOrder;
import com.evops.entity.PartInbound;
import com.evops.entity.PartInstallation;
import com.evops.entity.PartReplacement;
import com.evops.entity.PartRequisition;
import com.evops.entity.PartReturn;
import com.evops.entity.PartTraceRule;
import com.evops.entity.SparePart;
import com.evops.enums.DefectStatus;
import com.evops.enums.PartStatus;
import com.evops.enums.PartType;
import com.evops.enums.RequisitionStatus;
import com.evops.enums.ReturnStatus;
import com.evops.enums.VersionStatus;
import com.evops.mapper.PartInboundMapper;
import com.evops.mapper.PartInstallationMapper;
import com.evops.mapper.PartReplacementMapper;
import com.evops.mapper.PartRequisitionMapper;
import com.evops.mapper.PartReturnMapper;
import com.evops.mapper.PartTraceRuleMapper;
import com.evops.mapper.SparePartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 风电叶片备件库存：叶片螺栓、传感器、涂层材料。
 *
 * 口径：
 * - 备件按序列号和批次入库，序列号全局唯一；
 * - 维修领用绑定风机与缺陷单；已安装（INSTALLED）备件不能再次出库；
 * - 换件记录旧件去向与新件状态，装机台账窗口 [installedAt, removedAt) 与叶片台账同一口径；
 * - 退库须经验收，通过才回到在库；
 * - 库存、维修进度（缺陷/工单）与资产档案（装机台账）在同一事务中更新；
 * - 序列追踪规则调整只生成新版本，旧版本内容与旧追踪结论不改动。
 */
@Service
public class SparePartService extends ServiceImpl<SparePartMapper, SparePart> {

    private final PartInboundMapper inboundMapper;
    private final PartRequisitionMapper requisitionMapper;
    private final PartInstallationMapper installationMapper;
    private final PartReplacementMapper replacementMapper;
    private final PartReturnMapper returnMapper;
    private final PartTraceRuleMapper ruleMapper;
    private final TurbineService turbineService;
    private final InspectionService inspectionService;
    private final AuditService auditService;

    public SparePartService(PartInboundMapper inboundMapper, PartRequisitionMapper requisitionMapper,
                            PartInstallationMapper installationMapper, PartReplacementMapper replacementMapper,
                            PartReturnMapper returnMapper, PartTraceRuleMapper ruleMapper,
                            TurbineService turbineService, InspectionService inspectionService,
                            AuditService auditService) {
        this.inboundMapper = inboundMapper;
        this.requisitionMapper = requisitionMapper;
        this.installationMapper = installationMapper;
        this.replacementMapper = replacementMapper;
        this.returnMapper = returnMapper;
        this.ruleMapper = ruleMapper;
        this.turbineService = turbineService;
        this.inspectionService = inspectionService;
        this.auditService = auditService;
    }

    // ---------------- 入库 ----------------

    /**
     * 入库：按序列号和批次入库。入库单与备件档案同事务落库，
     * 并按当前发布的序列追踪规则生成追踪结论（结论引用规则版本，之后不随规则调整改变）。
     */
    @Transactional
    public PartInbound inbound(InboundRequest req) {
        PartTraceRule rule = mustGetCurrentRule(req.getPartType());
        LocalDateTime stockedAt = req.getStockedAt() != null ? req.getStockedAt() : LocalDateTime.now();

        // 序列号全局唯一：批次内不得重复，也不得与库存档案重复
        Set<String> seen = new HashSet<>();
        for (InboundRequest.InboundItem item : req.getItems()) {
            if (!seen.add(item.getSerialNo())) {
                throw new IllegalArgumentException("本批序列号重复: " + item.getSerialNo());
            }
            Long dup = count(new QueryWrapper<SparePart>().eq("serial_no", item.getSerialNo()));
            if (dup > 0) {
                throw new IllegalArgumentException("序列号已入库: " + item.getSerialNo());
            }
            if (StringUtils.hasText(item.getPartNo())) {
                Long dupNo = count(new QueryWrapper<SparePart>().eq("part_no", item.getPartNo()));
                if (dupNo > 0) {
                    throw new IllegalArgumentException("备件编号已存在: " + item.getPartNo());
                }
            }
        }

        String inboundNo = StringUtils.hasText(req.getInboundNo())
                ? req.getInboundNo() : "IN" + System.currentTimeMillis();
        Long dupInbound = inboundMapper.selectCount(
                new QueryWrapper<PartInbound>().eq("inbound_no", inboundNo));
        if (dupInbound > 0) {
            throw new IllegalArgumentException("入库单号已存在: " + inboundNo);
        }

        PartInbound inbound = new PartInbound();
        inbound.setInboundNo(inboundNo);
        inbound.setBatchNo(req.getBatchNo());
        inbound.setPartType(req.getPartType());
        inbound.setQuantity(req.getItems().size());
        inbound.setOperator(req.getOperator());
        inbound.setRemark(req.getRemark());
        inbound.setRuleVersionId(rule.getId());
        inbound.setConclusion("入库校验通过：" + req.getItems().size() + " 件 " + req.getPartType()
                + " 按 " + rule.getTraceLevel() + " 级别追踪（规则 v" + rule.getVersionNo() + "）");
        inbound.setStockedAt(stockedAt);
        inboundMapper.insert(inbound);

        List<SparePart> parts = new ArrayList<>();
        for (InboundRequest.InboundItem item : req.getItems()) {
            SparePart part = new SparePart();
            part.setPartNo(StringUtils.hasText(item.getPartNo()) ? item.getPartNo() : "P-" + item.getSerialNo());
            part.setSerialNo(item.getSerialNo());
            part.setBatchNo(req.getBatchNo());
            part.setInboundId(inbound.getId());
            part.setPartType(req.getPartType());
            part.setName(req.getName());
            part.setSpec(req.getSpec());
            part.setStatus(PartStatus.IN_STOCK);
            part.setStockedAt(stockedAt);
            parts.add(part);
        }
        saveBatch(parts);

        auditService.record("PART_INBOUND", inbound.getId(), "INBOUND",
                "批次 " + req.getBatchNo() + " 入库 " + parts.size() + " 件 " + req.getPartType(),
                req.getOperator());
        return inbound;
    }

    // ---------------- 维修领用 ----------------

    /**
     * 维修领用：绑定风机与缺陷单。仅在库备件可出库——已领用、已安装、已退役备件不能再次出库。
     * 库存状态与领用单同事务更新。
     */
    @Transactional
    public PartRequisition issue(IssueRequest req) {
        turbineService.mustGet(req.getTurbineId());
        Defect defect = inspectionService.mustGetDefect(req.getDefectId());
        InspectionOrder order = inspectionService.mustGetOrder(defect.getOrderId());
        if (!order.getTurbineId().equals(req.getTurbineId())) {
            throw new IllegalArgumentException("缺陷单不属于该风机: " + req.getDefectId());
        }
        if (defect.getStatus() != DefectStatus.OPEN) {
            throw new IllegalArgumentException("缺陷不在待维修状态，不能领用: " + defect.getStatus());
        }
        SparePart part = mustGet(req.getPartId());
        if (part.getStatus() != PartStatus.IN_STOCK) {
            throw new IllegalArgumentException("备件不在库，不能出库: " + part.getSerialNo()
                    + " 当前状态 " + part.getStatus());
        }

        part.setStatus(PartStatus.ISSUED);
        updateById(part);

        String reqNo = StringUtils.hasText(req.getReqNo())
                ? req.getReqNo() : "RQ" + System.currentTimeMillis();
        Long dupReq = requisitionMapper.selectCount(
                new QueryWrapper<PartRequisition>().eq("req_no", reqNo));
        if (dupReq > 0) {
            throw new IllegalArgumentException("领用单号已存在: " + reqNo);
        }
        PartRequisition requisition = new PartRequisition();
        requisition.setReqNo(reqNo);
        requisition.setTurbineId(req.getTurbineId());
        requisition.setDefectId(req.getDefectId());
        requisition.setPartId(part.getId());
        requisition.setIssuedBy(req.getIssuedBy());
        requisition.setIssuedAt(LocalDateTime.now());
        requisition.setPurpose(req.getPurpose());
        requisition.setStatus(RequisitionStatus.ISSUED);
        requisitionMapper.insert(requisition);

        auditService.record("SPARE_PART", part.getId(), "ISSUE",
                "领用出库绑定风机 " + req.getTurbineId() + " 缺陷单 " + req.getDefectId(), req.getIssuedBy());
        return requisition;
    }

    // ---------------- 换件 ----------------

    /**
     * 换件：旧件拆下登记去向，新件装机记录状态。
     * 库存（备件状态）、维修进度（缺陷/工单）与资产档案（装机台账）在同一事务中更新；
     * 任一校验不过整体不生效。
     */
    @Transactional
    public PartReplacement replace(Long requisitionId, PartReplaceRequest req) {
        PartRequisition requisition = mustGetRequisition(requisitionId);
        if (requisition.getStatus() != RequisitionStatus.ISSUED) {
            throw new IllegalArgumentException("领用单状态不允许换件: " + requisition.getStatus());
        }
        SparePart newPart = mustGet(requisition.getPartId());
        if (newPart.getStatus() != PartStatus.ISSUED) {
            throw new IllegalArgumentException("新件不在已领用状态，不能装机: " + newPart.getStatus());
        }
        Defect defect = inspectionService.mustGetDefect(requisition.getDefectId());
        if (defect.getStatus() == DefectStatus.ACCEPTED) {
            throw new IllegalArgumentException("缺陷已关闭，不能换件: " + defect.getId());
        }
        PartTraceRule rule = mustGetCurrentRule(newPart.getPartType());
        LocalDateTime replacedAt = req.getReplacedAt() != null ? req.getReplacedAt() : LocalDateTime.now();

        // 旧件：同风机同类型当前在装（装机台账窗口未闭合）
        PartInstallation oldInstall = installationMapper.selectOne(new QueryWrapper<PartInstallation>()
                .eq("turbine_id", requisition.getTurbineId())
                .eq("part_type", newPart.getPartType())
                .isNull("removed_at")
                .last("LIMIT 1"));
        if (oldInstall != null) {
            if (req.getOldDisposition() == null) {
                throw new IllegalArgumentException("存在在装旧件，必须登记旧件去向");
            }
            if (replacedAt.isBefore(oldInstall.getInstalledAt())) {
                throw new IllegalArgumentException("换件时刻早于旧件装机时刻: " + replacedAt);
            }
        }

        // 旧件拆下：装机台账窗口闭合 [installedAt, replacedAt)，登记去向
        SparePart oldPart = null;
        if (oldInstall != null) {
            oldInstall.setRemovedAt(replacedAt);
            oldInstall.setDisposition(req.getOldDisposition());
            oldInstall.setDispositionNote(req.getDispositionNote());
            installationMapper.updateById(oldInstall);

            oldPart = mustGet(oldInstall.getPartId());
            oldPart.setStatus(PartStatus.RETIRED);
            oldPart.setDisposition(req.getOldDisposition());
            oldPart.setDisposedAt(replacedAt);
            updateById(oldPart);
        }

        // 新件装机：库存状态与资产档案同步更新
        newPart.setStatus(PartStatus.INSTALLED);
        updateById(newPart);
        requisition.setStatus(RequisitionStatus.INSTALLED);
        requisitionMapper.updateById(requisition);

        PartInstallation installation = new PartInstallation();
        installation.setPartId(newPart.getId());
        installation.setRequisitionId(requisitionId);
        installation.setTurbineId(requisition.getTurbineId());
        installation.setDefectId(requisition.getDefectId());
        installation.setPartType(newPart.getPartType());
        installation.setInstalledAt(replacedAt);
        installation.setRuleVersionId(rule.getId());
        installation.setConclusion("新件 " + newPart.getSerialNo() + " 装机，按 "
                + rule.getTraceLevel() + " 级别追踪（规则 v" + rule.getVersionNo() + "）");
        installationMapper.insert(installation);

        // 维修进度：换件完成即回填维修结果（缺陷仍待维修时）
        if (defect.getStatus() == DefectStatus.OPEN) {
            RepairRequest repair = new RepairRequest();
            repair.setRepairResult(StringUtils.hasText(req.getRepairResult())
                    ? req.getRepairResult()
                    : "更换 " + newPart.getPartType() + "：新件 " + newPart.getSerialNo()
                            + (oldPart != null ? "，旧件 " + oldPart.getSerialNo()
                                    + " 去向 " + req.getOldDisposition() : ""));
            inspectionService.repair(defect.getId(), repair);
        }

        // 换件记录：旧件去向与新件状态的前后关系
        PartReplacement record = new PartReplacement();
        record.setTurbineId(requisition.getTurbineId());
        record.setDefectId(requisition.getDefectId());
        record.setRequisitionId(requisitionId);
        record.setPartType(newPart.getPartType());
        record.setOldPartId(oldPart != null ? oldPart.getId() : null);
        record.setOldSerialNo(oldPart != null ? oldPart.getSerialNo() : null);
        record.setOldDisposition(oldPart != null ? req.getOldDisposition() : null);
        record.setNewPartId(newPart.getId());
        record.setNewSerialNo(newPart.getSerialNo());
        record.setNewPartStatus(PartStatus.INSTALLED.name());
        record.setReplacedAt(replacedAt);
        record.setRuleVersionId(rule.getId());
        record.setConclusion(oldPart != null
                ? "旧件 " + oldPart.getSerialNo() + " 去向 " + req.getOldDisposition()
                        + "，新件 " + newPart.getSerialNo() + " 状态 INSTALLED（规则 v" + rule.getVersionNo() + "）"
                : "无在装旧件，新件 " + newPart.getSerialNo() + " 状态 INSTALLED（规则 v"
                        + rule.getVersionNo() + "）");
        record.setOperator(req.getOperator());
        replacementMapper.insert(record);

        auditService.record("SPARE_PART", newPart.getId(), "REPLACE",
                record.getConclusion(), req.getOperator());
        return record;
    }

    // ---------------- 退库 ----------------

    /**
     * 退库申请：仅已领用（未装机）备件可退库；已安装备件不能退库。
     * 退库须经验收，申请后备件进入待验收状态。
     */
    @Transactional
    public PartReturn returnPart(Long requisitionId, PartReturnRequest req) {
        PartRequisition requisition = mustGetRequisition(requisitionId);
        if (requisition.getStatus() != RequisitionStatus.ISSUED) {
            throw new IllegalArgumentException("领用单状态不允许退库: " + requisition.getStatus());
        }
        SparePart part = mustGet(requisition.getPartId());
        if (part.getStatus() != PartStatus.ISSUED) {
            throw new IllegalArgumentException("备件不在已领用状态，不能退库: " + part.getStatus());
        }

        part.setStatus(PartStatus.RETURN_PENDING);
        updateById(part);

        String returnNo = StringUtils.hasText(req.getReturnNo())
                ? req.getReturnNo() : "RT" + System.currentTimeMillis();
        Long dupReturn = returnMapper.selectCount(
                new QueryWrapper<PartReturn>().eq("return_no", returnNo));
        if (dupReturn > 0) {
            throw new IllegalArgumentException("退库单号已存在: " + returnNo);
        }
        PartReturn partReturn = new PartReturn();
        partReturn.setReturnNo(returnNo);
        partReturn.setPartId(part.getId());
        partReturn.setRequisitionId(requisitionId);
        partReturn.setReturnedBy(req.getReturnedBy());
        partReturn.setReturnedAt(LocalDateTime.now());
        partReturn.setStatus(ReturnStatus.PENDING);
        returnMapper.insert(partReturn);

        auditService.record("SPARE_PART", part.getId(), "RETURN_APPLY",
                "退库申请，待验收", req.getReturnedBy());
        return partReturn;
    }

    /**
     * 退库验收：通过则回到在库、领用单闭环；驳回则退回已领用。
     * 库存状态与验收结论同事务更新。
     */
    @Transactional
    public PartReturn acceptReturn(Long returnId, ReturnAcceptRequest req) {
        PartReturn partReturn = returnMapper.selectById(returnId);
        if (partReturn == null) {
            throw new IllegalArgumentException("退库单不存在: " + returnId);
        }
        if (partReturn.getStatus() != ReturnStatus.PENDING) {
            throw new IllegalArgumentException("退库单已验收，不能重复验收: " + partReturn.getStatus());
        }
        SparePart part = mustGet(partReturn.getPartId());
        if (part.getStatus() != PartStatus.RETURN_PENDING) {
            throw new IllegalArgumentException("备件不在待验收状态: " + part.getStatus());
        }
        PartRequisition requisition = mustGetRequisition(partReturn.getRequisitionId());

        partReturn.setAcceptedBy(req.getAcceptedBy());
        partReturn.setAcceptedAt(LocalDateTime.now());
        partReturn.setAcceptRemark(req.getRemark());
        if (Boolean.TRUE.equals(req.getPass())) {
            part.setStatus(PartStatus.IN_STOCK);
            partReturn.setStatus(ReturnStatus.ACCEPTED);
            requisition.setStatus(RequisitionStatus.RETURNED);
        } else {
            // 验收驳回：退回已领用
            part.setStatus(PartStatus.ISSUED);
            partReturn.setStatus(ReturnStatus.REJECTED);
        }
        updateById(part);
        returnMapper.updateById(partReturn);
        requisitionMapper.updateById(requisition);

        auditService.record("SPARE_PART", part.getId(), "RETURN_ACCEPT",
                "退库验收 " + (Boolean.TRUE.equals(req.getPass()) ? "通过回库" : "驳回退回已领用"),
                req.getAcceptedBy());
        return partReturn;
    }

    // ---------------- 序列追踪规则 ----------------

    /** 当前发布版本的规则 */
    public PartTraceRule mustGetCurrentRule(PartType partType) {
        PartTraceRule rule = ruleMapper.selectOne(new QueryWrapper<PartTraceRule>()
                .eq("part_type", partType)
                .eq("status", VersionStatus.PUBLISHED)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        if (rule == null) {
            throw new IllegalArgumentException("未配置序列追踪规则: " + partType);
        }
        return rule;
    }

    /** 规则全量版本（含已退役），旧版本内容可查 */
    public List<PartTraceRule> listRules(PartType partType) {
        return ruleMapper.selectList(new QueryWrapper<PartTraceRule>()
                .eq(partType != null, "part_type", partType)
                .orderByAsc("part_type", "version_no"));
    }

    /**
     * 规则调整：只生成新版本（版本号递增并发布），旧版本退役但内容不改；
     * 已产生的入库/装机/换件结论继续引用旧版本，不随调整改变。
     */
    @Transactional
    public PartTraceRule adjustRule(PartType partType, TraceRuleAdjustRequest req) {
        PartTraceRule current = mustGetCurrentRule(partType);

        PartTraceRule next = new PartTraceRule();
        next.setPartType(partType);
        next.setVersionNo(current.getVersionNo() + 1);
        next.setTraceLevel(req.getTraceLevel());
        next.setNeedInboundCheck(req.getNeedInboundCheck());
        next.setMaxStockMonths(req.getMaxStockMonths());
        next.setRuleContent(req.getRuleContent());
        next.setStatus(VersionStatus.PUBLISHED);
        next.setPublishedAt(LocalDateTime.now());
        ruleMapper.insert(next);

        // 旧版本仅退役，内容保持不动
        current.setStatus(VersionStatus.RETIRED);
        ruleMapper.updateById(current);

        auditService.record("PART_TRACE_RULE", next.getId(), "ADJUST_RULE",
                partType + " 规则调整生成 v" + next.getVersionNo() + "，v" + current.getVersionNo()
                        + " 退役，旧结论继续引用旧版本",
                req.getOperator());
        return next;
    }

    // ---------------- 查询 ----------------

    /** 库存列表（可按类型/状态过滤） */
    public List<SparePart> listParts(PartType partType, PartStatus status) {
        return list(new QueryWrapper<SparePart>()
                .eq(partType != null, "part_type", partType)
                .eq(status != null, "status", status)
                .orderByAsc("part_type", "serial_no"));
    }

    /** 序列追踪：从入库到装机/退役的全生命周期，结论随记录留痕 */
    public PartTraceDto trace(Long partId) {
        SparePart part = mustGet(partId);
        PartTraceDto dto = new PartTraceDto();
        dto.setPart(part);
        dto.setInbound(inboundMapper.selectById(part.getInboundId()));
        dto.setRequisitions(requisitionMapper.selectList(new QueryWrapper<PartRequisition>()
                .eq("part_id", partId).orderByAsc("id")));
        dto.setInstallations(installationMapper.selectList(new QueryWrapper<PartInstallation>()
                .eq("part_id", partId).orderByAsc("id")));
        dto.setReplacementsAsNew(replacementMapper.selectList(new QueryWrapper<PartReplacement>()
                .eq("new_part_id", partId).orderByAsc("id")));
        dto.setReplacementsAsOld(replacementMapper.selectList(new QueryWrapper<PartReplacement>()
                .eq("old_part_id", partId).orderByAsc("id")));
        dto.setReturns(returnMapper.selectList(new QueryWrapper<PartReturn>()
                .eq("part_id", partId).orderByAsc("id")));
        return dto;
    }

    public SparePart mustGet(Long id) {
        SparePart part = getById(id);
        if (part == null) {
            throw new IllegalArgumentException("备件不存在: " + id);
        }
        return part;
    }

    public PartRequisition mustGetRequisition(Long id) {
        PartRequisition requisition = requisitionMapper.selectById(id);
        if (requisition == null) {
            throw new IllegalArgumentException("领用单不存在: " + id);
        }
        return requisition;
    }
}
