package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectStoryGenerateDTO {

    @NotBlank(message = "generation type is required")
    private String generationType;
    private Long targetJobId;
}
