package com.codecoachai.interview.domain.vo;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SseEventVO {

    private String type;
    private String requestId;
    private Long interviewId;
    private Long sessionId;
    private Long reportId;
    private String stage;
    private String message;
    private String content;
    private Integer index;
    private Long messageId;
    private Long aiCallLogId;
    private String fullContent;
    private String code;
    private Object result;
    private Map<String, Object> metadata;
}
