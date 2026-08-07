package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
public class JobSearchExperimentRelationSaveDTO {

    @NotBlank(message = "Relation type is required")
    private String relationType;

    @NotNull(message = "Relation id is required")
    private Long relationId;

    private String relationSummary;
    private Map<String, Object> metadata;
}
