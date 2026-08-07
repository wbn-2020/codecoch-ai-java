package com.codecoachai.task.feign.dto;

import lombok.Data;

@Data
public class CompleteResumeParseDTO {

    /** AI 解析返回的结构化 JSON */
    private String structuredJson;

    /** Extracted resume text for analysis result preview. */
    private String rawText;

    /** 解析状态：SUCCESS / FAILED */
    private String parseStatus;

    /** 失败原因（status=FAILED 时） */
    private String errorMessage;

    /** AI 模型名（路由轨迹，例：deepseek） */
    private String modelTrace;
}
