package com.codecoachai.resume.careercontact.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CareerInterviewRoundContactSaveDTO {
    @NotNull
    private Long contactId;
    private String relationshipType;
}
