package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewListVO {

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
    private Integer answeredQuestionCount;
    private LocalDateTime updatedAt;
}
