package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.InboundRequest;
import com.evops.dto.IssueRequest;
import com.evops.dto.PartReplaceRequest;
import com.evops.dto.PartReturnRequest;
import com.evops.dto.PartTraceDto;
import com.evops.dto.ReturnAcceptRequest;
import com.evops.entity.PartInbound;
import com.evops.entity.PartReplacement;
import com.evops.entity.PartRequisition;
import com.evops.entity.PartReturn;
import com.evops.entity.SparePart;
import com.evops.enums.PartStatus;
import com.evops.enums.PartType;
import com.evops.service.SparePartService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spare-parts")
public class SparePartController {

    private final SparePartService sparePartService;

    public SparePartController(SparePartService sparePartService) {
        this.sparePartService = sparePartService;
    }

    /** 入库：按序列号和批次入库，生成追踪结论 */
    @PostMapping("/inbound")
    public ApiResponse<PartInbound> inbound(@Validated @RequestBody InboundRequest req) {
        return ApiResponse.ok(sparePartService.inbound(req));
    }

    /** 库存列表（?partType=&status=） */
    @GetMapping
    public ApiResponse<List<SparePart>> list(@RequestParam(required = false) PartType partType,
                                             @RequestParam(required = false) PartStatus status) {
        return ApiResponse.ok(sparePartService.listParts(partType, status));
    }

    /** 备件详情 */
    @GetMapping("/{id}")
    public ApiResponse<SparePart> detail(@PathVariable Long id) {
        return ApiResponse.ok(sparePartService.mustGet(id));
    }

    /** 序列追踪：入库 → 领用 → 装机/退役 全生命周期 */
    @GetMapping("/{id}/trace")
    public ApiResponse<PartTraceDto> trace(@PathVariable Long id) {
        return ApiResponse.ok(sparePartService.trace(id));
    }

    /** 维修领用：绑定风机与缺陷单；已安装备件不能再次出库 */
    @PostMapping("/requisitions")
    public ApiResponse<PartRequisition> issue(@Validated @RequestBody IssueRequest req) {
        return ApiResponse.ok(sparePartService.issue(req));
    }

    /** 换件：旧件去向 + 新件状态；库存/维修进度/资产档案同事务更新 */
    @PostMapping("/requisitions/{id}/replace")
    public ApiResponse<PartReplacement> replace(@PathVariable Long id,
                                                @Validated @RequestBody PartReplaceRequest req) {
        return ApiResponse.ok(sparePartService.replace(id, req));
    }

    /** 退库申请：仅已领用（未装机）备件可退库 */
    @PostMapping("/requisitions/{id}/return")
    public ApiResponse<PartReturn> returnPart(@PathVariable Long id,
                                              @Validated @RequestBody PartReturnRequest req) {
        return ApiResponse.ok(sparePartService.returnPart(id, req));
    }

    /** 退库验收：通过回库 / 驳回退回已领用 */
    @PostMapping("/returns/{id}/accept")
    public ApiResponse<PartReturn> acceptReturn(@PathVariable Long id,
                                                @Validated @RequestBody ReturnAcceptRequest req) {
        return ApiResponse.ok(sparePartService.acceptReturn(id, req));
    }
}
