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
    /** 间隔复习档位（天）：1/3/7/15 */
    private Integer reviewIntervalDays;
    /** 下次复习到期时间 */
    private LocalDateTime nextReviewAt;
    /** 是否已到期可复习 */
    private Boolean reviewDue;
}
