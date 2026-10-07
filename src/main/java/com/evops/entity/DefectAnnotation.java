package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 影像标注：巡检员在无人机影像上标注位置、尺寸与截图，是缺陷分类反查的入口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_annotation")
public class DefectAnnotation extends BaseEntity {
    private Long defectId;
    private Long orderId;
    /** 被标注的影像地址（巡检工单影像） */
    private String imageUrl;
    /** 标注点在影像上的归一化/像素坐标 */
    private Double positionX;
    private Double positionY;
    /** 标注尺寸：长、宽（毫米） */
    private Double lengthMm;
    private Double widthMm;
    /** 标注截图地址 */
    private String screenshotUrl;
    /** 标注巡检员 */
    private String inspector;
    private String remark;
}
