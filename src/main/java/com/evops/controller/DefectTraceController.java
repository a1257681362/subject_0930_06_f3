package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.DefectTraceDto;
import com.evops.service.DefectLifecycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 影像标注反查：从无人机影像标注反查缺陷分类、原巡检单、处理人与异常原因。
 */
@RestController
@RequestMapping("/api/defect-traces")
public class DefectTraceController {

    private final DefectLifecycleService defectLifecycleService;

    public DefectTraceController(DefectLifecycleService defectLifecycleService) {
        this.defectLifecycleService = defectLifecycleService;
    }

    /** 按标注所在影像地址反查（一张影像可对应多条标注/缺陷） */
    @GetMapping("/by-image")
    public ApiResponse<List<DefectTraceDto>> byImage(@RequestParam String imageUrl) {
        return ApiResponse.ok(defectLifecycleService.traceByImage(imageUrl));
    }

    /** 按标注 id 精确定位一条 */
    @GetMapping("/annotations/{annotationId}")
    public ApiResponse<DefectTraceDto> byAnnotation(@PathVariable Long annotationId) {
        return ApiResponse.ok(defectLifecycleService.traceByAnnotation(annotationId));
    }
}
