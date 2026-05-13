package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewListVO {

    private Long id;
    private String title;
    private String mode;
    private String status;
    private String reportStatus;
    private Integer answeredQuestionCount;
    private LocalDateTime updatedAt;
}
