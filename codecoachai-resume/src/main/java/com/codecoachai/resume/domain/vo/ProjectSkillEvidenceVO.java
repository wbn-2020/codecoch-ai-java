package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectSkillEvidenceVO {

    private Long id;
    private Long userId;
    private Long projectEvidenceId;
    private String skillName;
    private String skillCategory;
    private String evidenceText;
    private String strengthLevel;
    private String jdKeyword;
    private String riskPoints;
    private String sourceType;
    private Boolean confirmed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
