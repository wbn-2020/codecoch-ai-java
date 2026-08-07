package com.codecoachai.resume.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerEvidenceUsageResultWriteDTO {

    @NotBlank(message = "结果来源类型不能为空")
    private String eventType;
    @NotNull(message = "结果来源 ID 不能为空")
    private Long eventId;
    @NotBlank(message = "结果代码不能为空")
    private String outcomeCode;
    private List<String> knownFacts = new ArrayList<>();
    private String externalFeedbackText;
    private String userInterpretationText;
    private List<String> unknowns = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime occurredAt;
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;
}
