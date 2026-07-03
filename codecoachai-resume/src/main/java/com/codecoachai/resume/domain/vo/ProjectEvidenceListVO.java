package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ProjectEvidenceListVO {

    private Long id;
    private Long userId;
    private String title;
    private String role;
    private String techStack;
    private Integer completenessScore;
    private String completenessStatus;
    private List<String> missingFields;
    private Long sourceResumeId;
    private Long sourceResumeProjectId;
    private Boolean sourceAvailable;
    private Long targetJobId;
    private Long skillEvidenceCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
