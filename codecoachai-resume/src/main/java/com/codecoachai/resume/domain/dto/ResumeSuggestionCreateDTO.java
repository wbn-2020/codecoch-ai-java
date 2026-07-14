package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ResumeSuggestionCreateDTO {
    @NotNull
    private Long sourceResumeVersionId;
    @Size(max = 40)
    private String sourceType;
    private Long sourceId;
    @Size(max = 64)
    private String sourceVersion;
    @NotBlank
    @Size(max = 64)
    private String sectionKey;
    @Size(max = 128)
    private String sectionId;
    @Size(max = 255)
    private String fieldPath;
    @NotNull
    @Min(0)
    @Max(524288)
    private Integer anchorStart;
    @NotNull
    @Min(0)
    @Max(524288)
    private Integer anchorEnd;
    @NotBlank
    @Size(max = 20000)
    private String originalText;
    @NotBlank
    @Size(max = 20000)
    private String suggestedText;
    @Size(max = 2000)
    private String rationale;
    @Size(max = 16)
    private String riskLevel;
    @Size(max = 20)
    private List<Map<String, Object>> evidenceReferences = new ArrayList<>();
}
