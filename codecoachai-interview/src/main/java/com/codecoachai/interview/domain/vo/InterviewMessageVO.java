package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewMessageVO {

    private Long id;
    private Long questionId;
    private String role;
    private String messageType;
    private String content;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
