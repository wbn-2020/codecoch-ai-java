package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WrongQuestionVO {

    private Long recordId;
    private Long questionId;
    private String title;
    private String masteryStatus;
    private LocalDateTime lastAnswerAt;
}
