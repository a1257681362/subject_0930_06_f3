package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.HandlingRuleRequest;
import com.evops.entity.DefectHandlingRule;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectType;
import com.evops.mapper.DefectHandlingRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 处置时限规则：裂纹/雷击/结冰/涂层剥落 按等级配置处置时限、默认责任人与维修建议模板。
 * 类型+等级唯一；调整只影响之后登记/复核的缺陷，已生成的处置时限不回改。
 */
@Service
public class HandlingRuleService extends ServiceImpl<DefectHandlingRuleMapper, DefectHandlingRule> {

    /** 按类型+等级取规则（缺失即配置缺口，直接报错拦截） */
    public DefectHandlingRule mustGetRule(DefectType type, DefectLevel level) {
        DefectHandlingRule rule = getOne(new QueryWrapper<DefectHandlingRule>()
                .eq("defect_type", type)
                .eq("defect_level", level));
        if (rule == null) {
            throw new IllegalArgumentException(
                    "未配置处置时限规则: " + type + "/" + level);
        }
        return rule;
    }

    public List<DefectHandlingRule> listRules(DefectType type) {
        QueryWrapper<DefectHandlingRule> wrapper = new QueryWrapper<>();
        if (type != null) {
            wrapper.eq("defect_type", type);
        }
        return list(wrapper.orderByAsc("defect_type", "defect_level"));
    }

    /** 新增或按 类型+等级 覆盖配置 */
    @Transactional
    public DefectHandlingRule upsertRule(HandlingRuleRequest req) {
        DefectHandlingRule rule = getOne(new QueryWrapper<DefectHandlingRule>()
                .eq("defect_type", req.getDefectType())
                .eq("defect_level", req.getDefectLevel()));
        boolean isNew = rule == null;
        if (isNew) {
            rule = new DefectHandlingRule();
            rule.setDefectType(req.getDefectType());
            rule.setDefectLevel(req.getDefectLevel());
        }
        rule.setDeadlineHours(req.getDeadlineHours());
        rule.setDefaultResponsible(req.getDefaultResponsible());
        rule.setAdviceTemplate(req.getAdviceTemplate());
        if (isNew) {
            save(rule);
        } else {
            updateById(rule);
        }
        return rule;
    }
}
