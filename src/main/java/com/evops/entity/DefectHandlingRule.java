package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 处置时限规则：缺陷类型 + 等级 唯一，决定处置时限、默认责任人与维修建议模板。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_handling_rule")
public class DefectHandlingRule extends BaseEntity {
    private DefectType defectType;
    private DefectLevel defectLevel;
    /** 处置时限（小时） */
    private Integer deadlineHours;
    /** 默认责任人/班组 */
    private String defaultResponsible;
    /** 维修建议模板 */
    private String adviceTemplate;
}
