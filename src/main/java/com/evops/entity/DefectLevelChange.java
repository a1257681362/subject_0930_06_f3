package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.DefectLevel;
import com.evops.enums.ReviewDecision;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 风险等级变化留痕：升级/降级前后的等级、时限、维修建议、责任人均保留。
 * 初判不被覆盖，变化前后口径可追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_level_change")
public class DefectLevelChange extends BaseEntity {
    private Long defectId;
    /** UPGRADE 升级 / DOWNGRADE 降级 */
    private ReviewDecision changeType;
    private DefectLevel fromLevel;
    private DefectLevel toLevel;
    private Long ruleId;
    private LocalDateTime deadlineBefore;
    private LocalDateTime deadlineAfter;
    private String adviceBefore;
    private String adviceAfter;
    private String responsibleBefore;
    private String responsibleAfter;
    /** 复核专家 */
    private String reviewer;
    /** 异常原因 */
    private String anomalyReason;
    /** 复核意见 */
    private String opinion;
    private LocalDateTime changedAt;
}
