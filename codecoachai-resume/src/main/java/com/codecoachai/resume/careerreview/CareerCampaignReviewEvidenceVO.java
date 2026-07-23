package com.codecoachai.resume.careerreview;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerCampaignReviewEvidenceVO {

    private Long userId;
    private Long campaignId;
    private String campaignStatus;
    private String campaignTitle;
    private Boolean completed;
    private Boolean allOpportunitiesClosed;
    private Integer sampleSize;
    private LocalDateTime dataCutoffAt;
    private List<Fact> facts = new ArrayList<>();
    private List<Source> sources = new ArrayList<>();

    @Data
    public static class Fact {
        private String key;
        private String label;
        private Object value;
        private String sourceRef;
    }

    @Data
    public static class Source {
        private String sourceType;
        private Long sourceId;
        private Integer sourceVersion;
        private LocalDateTime sourceTime;
        private LocalDateTime sourceUpdatedAt;
        private String sourceHash;
    }
}
