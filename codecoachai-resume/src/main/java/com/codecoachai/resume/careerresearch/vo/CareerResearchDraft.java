package com.codecoachai.resume.careerresearch.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerResearchDraft {
    private List<Fact> facts = new ArrayList<>();
    private List<String> unknowns = new ArrayList<>();
    private List<String> sourceLimits = new ArrayList<>();
    private List<String> questionsToVerify = new ArrayList<>();
    private List<String> preparationFocus = new ArrayList<>();
    private List<String> riskSignals = new ArrayList<>();
    private List<Long> sourceRefs = new ArrayList<>();
    private String confidenceLevel;
    private String fallbackReason;
    private Long aiCallLogId;

    @Data
    public static class Fact {
        private String statement;
        private List<Long> sourceVersionIds = new ArrayList<>();
    }
}
