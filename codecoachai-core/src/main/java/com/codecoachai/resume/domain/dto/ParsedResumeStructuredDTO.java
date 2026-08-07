package com.codecoachai.resume.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParsedResumeStructuredDTO {

    private BasicInfo basicInfo;
    private String targetPosition;
    private List<String> skills = new ArrayList<>();
    private List<JsonNode> workExperiences = new ArrayList<>();
    private List<ProjectExperience> projectExperiences = new ArrayList<>();
    private List<JsonNode> educationExperiences = new ArrayList<>();

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
    public static class ProjectExperience {
        private String projectName;
        private String period;
        private String description;
        private List<String> techStack = new ArrayList<>();
        private List<String> responsibilities = new ArrayList<>();
        private List<String> technicalDifficulties = new ArrayList<>();
        private List<String> achievements = new ArrayList<>();
    }
}
