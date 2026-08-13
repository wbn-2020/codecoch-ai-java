package com.codecoachai.resume.careeroffer.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerOfferReminderCandidateVO {
    private Long offerId;
    private Long userId;
    private Long applicationId;
    private LocalDateTime decisionDeadline;
    private LocalDate reminderDate;
    private String bizType;
    private String bizId;
    private String idempotencyKey;
}
