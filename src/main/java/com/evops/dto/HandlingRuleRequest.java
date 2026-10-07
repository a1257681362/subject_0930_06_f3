package com.evops.dto;

import com.evops.enums.DefectLevel;
import com.evops.enums.DefectType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/** 处置时限规则配置入参：按 缺陷类型+等级 配置时限/责任人/建议模板 */
@Data
public class HandlingRuleRequest {
    @NotNull(message = "缺陷分类不能为空")
    private DefectType defectType;
    @NotNull(message = "缺陷等级不能为空")
    private DefectLevel defectLevel;
    @NotNull(message = "处置时限不能为空")
    @Positive(message = "处置时限必须为正整数小时")
    private Integer deadlineHours;
    @NotBlank(message = "默认责任人不能为空")
    private String defaultResponsible;
    @NotBlank(message = "维修建议模板不能为空")
    private String adviceTemplate;
}
