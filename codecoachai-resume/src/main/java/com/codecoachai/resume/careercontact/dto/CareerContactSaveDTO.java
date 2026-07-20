package com.codecoachai.resume.careercontact.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CareerContactSaveDTO {
    private Long applicationId;
    @NotBlank
    private String displayName;
    private String roleType;
    private String channelType;
    private String maskedContactHint;
    private String relationshipSummary;
    private String relationshipType;
}
