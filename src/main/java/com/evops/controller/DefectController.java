package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.AcceptRequest;
import com.evops.dto.AnnotationRequest;
import com.evops.dto.NoteRequest;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReviewRequest;
import com.evops.entity.Defect;
import com.evops.entity.DefectAnnotation;
import com.evops.entity.DefectNote;
import com.evops.service.DefectManageService;
import com.evops.service.InspectionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final InspectionService inspectionService;
    private final DefectManageService defectManageService;

    public DefectController(InspectionService inspectionService, DefectManageService defectManageService) {
        this.inspectionService = inspectionService;
        this.defectManageService = defectManageService;
    }

    /** 维修：回填维修结果 */
    @PostMapping("/{id}/repair")
    public ApiResponse<Defect> repair(@PathVariable Long id,
                                      @Validated @RequestBody RepairRequest req) {
        return ApiResponse.ok(inspectionService.repair(id, req));
    }

    /** 验收：通过闭环，驳回退回待维修 */
    @PostMapping("/{id}/accept")
    public ApiResponse<Defect> accept(@PathVariable Long id,
                                      @Validated @RequestBody AcceptRequest req) {
        return ApiResponse.ok(inspectionService.accept(id, req));
    }

    /** 影像标注：巡检员在影像上标注位置、尺寸与截图 */
    @PostMapping("/{id}/annotations")
    public ApiResponse<DefectAnnotation> annotate(@PathVariable Long id,
                                                  @Validated @RequestBody AnnotationRequest req) {
        return ApiResponse.ok(defectManageService.annotate(id, req));
    }

    /** 专家复核：可升级或降级，不覆盖初判；等级变化重生成建议并记录责任人 */
    @PostMapping("/{id}/review")
    public ApiResponse<Defect> review(@PathVariable Long id,
                                      @Validated @RequestBody ReviewRequest req) {
        return ApiResponse.ok(defectManageService.review(id, req));
    }

    /** 追加说明：已关闭缺陷唯一允许的写操作 */
    @PostMapping("/{id}/notes")
    public ApiResponse<DefectNote> addNote(@PathVariable Long id,
                                           @Validated @RequestBody NoteRequest req) {
        return ApiResponse.ok(defectManageService.addNote(id, req));
    }

    /** 缺陷详情：登记信息 + 标注 + 复核记录 + 追加说明 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(defectManageService.detail(id));
    }
}
