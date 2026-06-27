package com.codecoachai.task.feign.dto;

import lombok.Data;

@Data
public class CompleteInterviewReportDTO {

    private Long reportId;
    private String generationToken;

    /** 报告 JSON（结构化） */
    private String reportJson;

    /** 总分 */
    private Integer totalScore;

    /** 状态：SUCCESS / FAILED */
    private String reportStatus;

    /** 失败原因 */
    private String errorMessage;
}
