package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 缺陷追加说明入参：缺陷关闭后唯一允许的写入 */
@Data
public class DefectNoteRequest {
    @NotBlank(message = "说明内容不能为空")
    private String note;
    @NotBlank(message = "记录人不能为空")
    private String author;
}
