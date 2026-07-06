package com.codecoachai.ai.agent.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SuggestionQualityGateVO {

    private String gateStatus;
    private String suggestionStrength;
    private List<String> reasons = new ArrayList<>();
    private List<String> blockedConclusions = new ArrayList<>();
    private Integer sampleSize;
    private Integer minSampleSize;
}
