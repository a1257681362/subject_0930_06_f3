package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 缺陷登记：缺陷分类与等级、初判/复核分离、处置时限、维修建议、责任人、异常原因。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect")
public class Defect extends BaseEntity {
    private Long orderId;
    private Long bladeId;
    /** 缺陷分类：裂纹/雷击/结冰/涂层剥落 */
    private DefectType defectType;
    /** 当前风险等级（复核后可能变化） */
    private DefectLevel defectLevel;
    /** 巡检员初判等级：专家复核只升降级，不覆盖初判 */
    private DefectLevel initialLevel;
    /** 初判巡检员 */
    private String inspector;
    private String description;
    private DefectStatus status;
    /** 命中的处置时限规则 */
    private Long ruleId;
    /** 处置时限（登记时刻 + 规则时限） */
    private LocalDateTime deadline;
    /** 当前维修建议（风险等级变化时重新生成） */
    private String repairAdvice;
    /** 当前责任人（风险等级变化时随规则重定） */
    private String responsiblePerson;
    /** 复核专家 */
    private String reviewer;
    private LocalDateTime reviewedAt;
    /** 复核意见 */
    private String reviewOpinion;
    /** 异常原因：初判与复核不一致等 */
    private String anomalyReason;
    /** 维修结果 */
    private String repairResult;
    private LocalDateTime repairedAt;
    private LocalDateTime acceptedAt;
    /** 验收备注（含驳回原因） */
    private String acceptRemark;
    /** 关闭时刻（验收通过），关闭后只能追加说明 */
    private LocalDateTime closedAt;
}
