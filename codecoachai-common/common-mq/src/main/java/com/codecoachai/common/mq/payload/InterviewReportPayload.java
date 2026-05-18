package com.codecoachai.common.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面试报告生成任务负载。
 * Topic: codecoachai-interview  Tag: report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReportPayload {

    private Long sessionId;
    private Long userId;
    private String mode;
}
