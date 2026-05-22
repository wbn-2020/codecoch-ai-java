package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class ResumeApplyAiSuggestionDTO {
    private Long optimizeRecordId;
    private String suggestionType;
    private String status;
    private String note;
}
