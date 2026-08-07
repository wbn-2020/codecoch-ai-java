package com.codecoachai.resume.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerProjectEvidenceTrainingContextVO {

    private Long projectEvidenceId;
    private String title;
    private String role;
    private String techStack;
    private Integer completenessScore;
    private String completenessStatus;
    private List<String> missingFields;
    private String projectSummary;
    private List<String> topSkillNames;
    private List<InnerProjectSkillEvidenceSummaryVO> skillEvidenceSummaries;
}
