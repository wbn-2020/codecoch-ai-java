package com.codecoachai.resume.careerresearch.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerResearchSourceVersionCreateDTO {
    @NotBlank
    private String content;
    private String contentSummary;
    private LocalDateTime capturedAt;
}
