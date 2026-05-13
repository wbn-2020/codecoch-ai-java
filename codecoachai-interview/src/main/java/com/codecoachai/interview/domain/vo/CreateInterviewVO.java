package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class CreateInterviewVO {

    private Long id;
    private String title;
    private String mode;
    private String targetPosition;
    private String experienceLevel;
    private String industryDirection;
    private String difficulty;
    private String interviewerStyle;
    private Boolean basedOnResume;
    private String status;
    private String reportStatus;
    private List<InterviewStageVO> stages;
}
