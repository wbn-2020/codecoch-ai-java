package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewTranscriptConfirmDTO {

    @NotBlank(message = "confirmedText is required")
    @Size(max = 5000, message = "confirmedText must be 5000 characters or less")
    private String confirmedText;

    private Boolean lowConfidenceAcknowledged;
}
