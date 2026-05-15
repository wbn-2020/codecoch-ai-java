package com.codecoachai.resume.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class ResumeOptimizeAiRequestDTO {

    private Long optimizeRecordId;
    private Long userId;
    private Long resumeId;
    private String targetPosition;
    private Integer experienceYears;
    private String industryDirection;
    private ResumeSnapshot resume;
    private List<ProjectSnapshot> projects;

    @Data
    public static class ResumeSnapshot {
        private String realName;
        private String targetPosition;
        private String skillStack;
        private String workExperience;
        private String educationExperience;
        private String summary;
    }

    @Data
    public static class ProjectSnapshot {
        private Long projectId;
        private String projectName;
        private String projectBackground;
        private String role;
        private String techStack;
        private String responsibility;
        private String technicalDifficulties;
        private String optimizationResults;
        private String description;
        private String highlights;
    }
}
