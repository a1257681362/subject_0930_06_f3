package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.HandlingRuleRequest;
import com.evops.entity.DefectHandlingRule;
import com.evops.enums.DefectType;
import com.evops.service.HandlingRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 处置时限规则：裂纹/雷击/结冰/涂层剥落 按等级配置处置时限、责任人与维修建议模板。
 */
@RestController
@RequestMapping("/api/handling-rules")
public class HandlingRuleController {

    private final HandlingRuleService handlingRuleService;

    public HandlingRuleController(HandlingRuleService handlingRuleService) {
        this.handlingRuleService = handlingRuleService;
    }

    /** 规则列表（?defectType=CRACK 可按缺陷分类过滤） */
    @GetMapping
    public ApiResponse<List<DefectHandlingRule>> list(
            @RequestParam(required = false) DefectType defectType) {
        return ApiResponse.ok(handlingRuleService.listRules(defectType));
    }

    /** 新增或按 类型+等级 覆盖配置 */
    @PostMapping
    public ApiResponse<DefectHandlingRule> upsert(
            @Validated @RequestBody HandlingRuleRequest req) {
        return ApiResponse.ok(handlingRuleService.upsertRule(req));
    }
}
