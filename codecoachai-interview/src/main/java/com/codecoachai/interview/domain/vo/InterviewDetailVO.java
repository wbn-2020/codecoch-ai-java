package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InterviewDetailVO {

    private Long id;
    private String title;
    private String mode;
    private String targetPosition;
    private String experienceLevel;
    private Long industryTemplateId;
    private String industryDirection;
    private String industryContext;
    private String difficulty;
    private String interviewerStyle;
    private Boolean basedOnResume;
    private String status;
    private String reportStatus;
    private List<InterviewStageVO> stages;
    private List<InterviewMessageVO> messages;
}
