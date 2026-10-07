package com.evops.dto;

import lombok.Data;

/**
 * 影像标注反查结果：从一条标注定位原巡检单、缺陷分类、各环节处理人与异常原因。
 */
@Data
public class DefectTraceDto {
    // 标注
    private Long annotationId;
    private String imageUrl;
    private Double positionX;
    private Double positionY;
    private Double lengthMm;
    private Double widthMm;
    private String screenshotUrl;
    private String annotationInspector;
    private String annotationRemark;
    // 缺陷分类与等级（初判 vs 当前）
    private Long defectId;
    private String defectType;
    private String initialLevel;
    private String currentLevel;
    private String defectStatus;
    private String anomalyReason;
    // 处理人：初判巡检员 / 复核专家 / 维修责任人
    private String inspector;
    private String reviewer;
    private String responsiblePerson;
    // 原巡检单
    private Long orderId;
    private String orderNo;
    private Long turbineId;
    private java.time.LocalDateTime inspectTime;
    private String orderImageUrl;
}
