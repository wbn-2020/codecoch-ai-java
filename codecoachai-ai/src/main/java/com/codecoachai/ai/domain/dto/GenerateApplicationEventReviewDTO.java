package com.codecoachai.ai.domain.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateApplicationEventReviewDTO {

    private Long userId;
    private Long eventId;
    private Long applicationId;
    private Long targetJobId;
    private String scenario;
    private String eventScope;
    private String jobTitle;
    private String applicationSource;
    private String applicationStatus;
    private String eventType;
    private String eventTime;
    private String eventSummary;
    private List<Fact> facts = new ArrayList<>();
    private String selfReflection;
    private List<String> legacyHypotheses = new ArrayList<>();
    private String confidenceCeiling;

    @Data
    public static class Fact {

        private String id;
        private String content;
        private String owner;
        private String sourceType;
    }
}
