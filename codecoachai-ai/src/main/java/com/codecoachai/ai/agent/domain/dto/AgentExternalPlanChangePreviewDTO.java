package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentExternalPlanChangePreviewDTO {

    @NotBlank
    @Size(max = 40)
    private String sourceType;
    private Long sourceId;
    private Integer sourceVersion;
    @NotBlank
    @Size(max = 128)
    private String sourceContextHash;
    private Long targetJobId;
    private LocalDate targetDate;
    private Integer maxTotalMinutes = 120;
    @NotEmpty
    @Valid
    private List<AgentExternalPlanIntentDTO> intents = new ArrayList<>();
    @NotBlank
    @Size(min = 8, max = 128)
    private String idempotencyKey;
}
