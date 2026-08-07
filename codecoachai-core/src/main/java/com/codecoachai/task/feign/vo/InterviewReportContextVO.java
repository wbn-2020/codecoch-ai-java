package com.codecoachai.task.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class InterviewReportContextVO {

    private Long sessionId;
    private Long userId;
    private String mode;
    private String targetPosition;
    private String experienceLevel;
    private String industryDirection;
    private String industryContext;
    private String difficulty;
    private String resumeContent;
    private String projectContent;
    /** 历史问答消息（按时序），元素为 "Q: ...\nA: ..." 之类的格式 */
    private List<String> messages;
}
