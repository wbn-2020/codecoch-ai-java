package com.codecoachai.resume.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParsedResumeStructuredDTO {

    public static final String CURRENT_SCHEMA_VERSION = "resume-import-v1";

    private String schemaVersion;
    private BasicInfo basicInfo;
    private String targetPosition;
    private String summary;
    private List<String> skills = new ArrayList<>();
    private List<WorkExperience> workExperiences = new ArrayList<>();
    private List<ProjectExperience> projectExperiences = new ArrayList<>();
    private List<EducationExperience> educationExperiences = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BasicInfo {
        private String name;
        private String phone;
        private String email;
        private String location;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkExperience {
        private String company;
        private String position;
        private String period;
        private String description;
        private List<String> responsibilities = new ArrayList<>();
        private List<String> achievements = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectExperience {
        private String projectName;
        private String period;
        private String background;
        private String role;
        private String description;
        private List<String> techStack = new ArrayList<>();
        private List<String> responsibilities = new ArrayList<>();
        private List<String> coreFeatures = new ArrayList<>();
        private List<String> technicalDifficulties = new ArrayList<>();
        private List<String> optimizationResults = new ArrayList<>();
        private List<String> achievements = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EducationExperience {
        private String school;
        private String degree;
        private String major;
        private String period;
        private String description;
    }
}
