package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeSuggestionAdoptionVO {
    private Long id;
    private Long resumeId;
    private Long optimizeRecordId;
    private Long resumeVersionId;
    private String suggestionType;
    private String status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
