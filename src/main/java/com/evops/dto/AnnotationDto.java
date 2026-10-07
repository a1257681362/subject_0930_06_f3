package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 影像标注入参：巡检员在影像上标注位置、尺寸与截图 */
@Data
public class AnnotationDto {
    @NotBlank(message = "标注影像地址不能为空")
    private String imageUrl;
    @NotNull(message = "标注位置X坐标不能为空")
    private Double positionX;
    @NotNull(message = "标注位置Y坐标不能为空")
    private Double positionY;
    /** 尺寸：长（毫米） */
    private Double lengthMm;
    /** 尺寸：宽（毫米） */
    private Double widthMm;
    @NotBlank(message = "标注截图地址不能为空")
    private String screenshotUrl;
    private String remark;
}
