package com.codecoachai.resume.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class CareerEvidenceUsageResultCommandDTO {

    @NotNull(message = "预期锁版本不能为空")
    private Integer expectedLockVersion;
    private String outcomeCode;
    private List<String> knownFacts;
    private String externalFeedbackText;
    private String userInterpretationText;
    private List<String> unknowns;
    private List<String> limits;
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime occurredAt;
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;
    private String reason;
}
