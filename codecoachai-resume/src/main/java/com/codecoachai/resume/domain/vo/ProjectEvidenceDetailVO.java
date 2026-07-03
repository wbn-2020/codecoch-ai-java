package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ProjectEvidenceDetailVO {

    private Long id;
    private Long userId;
    private String title;
    private String role;
    private String startDate;
    private String endDate;
    private String background;
    private String responsibility;
    private String techStack;
    private String difficulty;
    private String solution;
    private String result;
    private String reflection;
    private Integer completenessScore;
    private String completenessStatus;
    private List<String> missingFields;
    private Long sourceResumeId;
    private Long sourceResumeProjectId;
    private Boolean sourceAvailable;
    private Long targetJobId;
    private List<ProjectSkillEvidenceVO> skillEvidences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
