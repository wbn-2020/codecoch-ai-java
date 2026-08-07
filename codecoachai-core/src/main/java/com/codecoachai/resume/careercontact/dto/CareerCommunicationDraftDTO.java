package com.codecoachai.resume.careercontact.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CareerCommunicationDraftDTO {
    private Long contactId;
    @NotBlank
    private String purpose;
    private String facts;
    private String tone;
}
