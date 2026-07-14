package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeSuggestionDecisionDTO {
    @NotBlank
    @Size(max = 24)
    private String decisionType;
    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
    @Size(max = 1000)
    private String note;
    @Size(max = 20000)
    private String editedText;
}
