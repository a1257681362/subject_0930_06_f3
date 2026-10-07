package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 缺陷追加说明：缺陷关闭后唯一允许的写入动作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_defect_note")
public class DefectNote extends BaseEntity {
    private Long defectId;
    private String note;
    private String author;
}
