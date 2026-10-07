package com.evops.dto;

import com.evops.enums.DefectLevel;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 专家复核入参：可升级/降级/维持，不覆盖初判 */
@Data
public class ReviewRequest {
    /** 复核后等级；与初判相同视为维持 */
    @NotNull(message = "复核等级不能为空")
    private DefectLevel reviewedLevel;
    @NotBlank(message = "复核专家不能为空")
    private String reviewer;
    /** 异常原因：升降级时建议填写（如影像误判、尺寸复测偏差） */
    private String anomalyReason;
    private String opinion;
}
