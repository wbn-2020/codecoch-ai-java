package com.codecoachai.ai.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class EvidenceLearningCandidateDecisionVO {
    private String candidateKey;
    private String title;
    private String content;
    private List<String> decisionOptions = new ArrayList<>();
    private Integer usageCount;
    private Integer sampleCount;
    private String confidenceLevel;
    private List<String> limits = new ArrayList<>();
    private List<EvidenceLearningSourceRefVO> sourceRefs = new ArrayList<>();
    private Boolean requiresUserConfirmation = true;
}
