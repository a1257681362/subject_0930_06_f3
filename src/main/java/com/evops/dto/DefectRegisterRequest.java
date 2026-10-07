package com.evops.dto;

import com.evops.enums.DefectLevel;
import com.evops.enums.DefectType;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/** 缺陷登记入参：初判分类/等级 + 巡检员 + 影像标注 */
@Data
public class DefectRegisterRequest {
    @NotNull(message = "叶片不能为空")
    private Long bladeId;
    @NotNull(message = "缺陷分类不能为空")
    private DefectType defectType;
    /** 巡检员初判等级 */
    @NotNull(message = "初判等级不能为空")
    private DefectLevel defectLevel;
    @NotBlank(message = "巡检员不能为空")
    private String inspector;
    private String description;
    /** 影像上标注的位置、尺寸与截图（至少一条） */
    @NotEmpty(message = "至少保留一条影像标注")
    @Valid
    private List<AnnotationDto> annotations;
}
