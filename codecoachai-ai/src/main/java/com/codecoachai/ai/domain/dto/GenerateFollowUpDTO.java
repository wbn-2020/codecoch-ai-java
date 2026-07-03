package com.codecoachai.ai.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class GenerateFollowUpDTO {

    private Long questionId;
    private String questionTitle;
    private String rootQuestionContent;
    private String currentQuestionContent;
    private String questionContent;
    private String referenceAnswer;
    private String answerContent;
    private String comment;
    private Integer followUpCount;
    private Integer maxFollowUpCount;
    private String currentStage;
    private String historySummary;
    private String knowledgePoints;
    private String industryContext;
    private String trainingScene;
    private String targetSkillDomain;
    private List<String> targetSkillCodes;
    private String targetLevel;
    private List<Long> projectEvidenceIds;
    private String projectEvidenceContext;
    private String trainingContextSummary;
    private String followUpIntensity;
}
