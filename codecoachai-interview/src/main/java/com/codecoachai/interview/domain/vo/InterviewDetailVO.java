package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InterviewDetailVO {

    private Long id;
    private String title;
    private String mode;
    private String status;
    private String reportStatus;
    private List<InterviewStageVO> stages;
    private List<InterviewMessageVO> messages;
}
