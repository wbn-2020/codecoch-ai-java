package com.codecoachai.resume.careercontact.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerActivitySaveDTO {
    private Long contactId;
    @NotBlank
    private String activityType;
    private String channelType;
    @NotBlank
    private String subject;
    @NotBlank
    private String summary;
    private LocalDateTime occurredAt;
    private LocalDateTime nextFollowUpAt;
    @NotBlank
    private String idempotencyKey;
}
