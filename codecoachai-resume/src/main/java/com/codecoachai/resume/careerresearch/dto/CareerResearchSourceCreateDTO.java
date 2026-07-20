package com.codecoachai.resume.careerresearch.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerResearchSourceCreateDTO {
    @NotBlank
    private String sourceType;
    @NotBlank
    private String title;
    private String officialUrl;
    private String externalRef;
    @NotBlank
    private String content;
    private String contentSummary;
    private LocalDateTime capturedAt;
}
