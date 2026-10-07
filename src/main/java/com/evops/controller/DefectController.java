package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.AcceptRequest;
import com.evops.dto.DefectNoteRequest;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReviewRequest;
import com.evops.entity.Defect;
import com.evops.entity.DefectAnnotation;
import com.evops.entity.DefectLevelChange;
import com.evops.entity.DefectNote;
import com.evops.service.DefectLifecycleService;
import com.evops.service.InspectionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final InspectionService inspectionService;
    private final DefectLifecycleService defectLifecycleService;

    public DefectController(InspectionService inspectionService,
                            DefectLifecycleService defectLifecycleService) {
        this.inspectionService = inspectionService;
        this.defectLifecycleService = defectLifecycleService;
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

    /** 专家复核：升级/降级/维持，不覆盖初判；风险等级变化重新生成维修建议并记录责任人 */
    @PostMapping("/{id}/review")
    public ApiResponse<Defect> review(@PathVariable Long id,
                                      @Validated @RequestBody ReviewRequest req) {
        return ApiResponse.ok(defectLifecycleService.review(id, req));
    }

    /** 追加说明：缺陷关闭后唯一允许的写入 */
    @PostMapping("/{id}/notes")
    public ApiResponse<DefectNote> addNote(@PathVariable Long id,
                                           @Validated @RequestBody DefectNoteRequest req) {
        return ApiResponse.ok(defectLifecycleService.addNote(id, req));
    }

    /** 缺陷详情：缺陷本体 + 影像标注 + 等级变化留痕 + 追加说明 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("defect", defectLifecycleService.mustGet(id));
        result.put("annotations", defectLifecycleService.listAnnotations(id));
        result.put("levelChanges", defectLifecycleService.listLevelChanges(id));
        result.put("notes", defectLifecycleService.listNotes(id));
        return ApiResponse.ok(result);
    }

    /** 影像标注列表 */
    @GetMapping("/{id}/annotations")
    public ApiResponse<List<DefectAnnotation>> annotations(@PathVariable Long id) {
        return ApiResponse.ok(defectLifecycleService.listAnnotations(id));
    }

    /** 风险等级变化留痕 */
    @GetMapping("/{id}/level-changes")
    public ApiResponse<List<DefectLevelChange>> levelChanges(@PathVariable Long id) {
        return ApiResponse.ok(defectLifecycleService.listLevelChanges(id));
    }
}
