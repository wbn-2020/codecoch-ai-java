package com.codecoachai.resume.feign.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateApplicationEventReviewAiVO {

    private String summary;
    private List<String> limits = new ArrayList<>();
    private List<Signal> signals = new ArrayList<>();
    private List<String> adjustments = new ArrayList<>();
    private List<String> nextActions = new ArrayList<>();
    private Long aiCallLogId;
    private String rawResponse;

    @Data
    public static class Signal {
        private String content;
        private List<String> factRefs = new ArrayList<>();
        private String confidenceLevel;
    }
}
