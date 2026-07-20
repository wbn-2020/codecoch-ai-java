package com.codecoachai.resume.careercampaign;

import java.time.LocalDateTime;
import lombok.Data;

public final class CareerCampaignModels {

    private CareerCampaignModels() {
    }

    @Data
    public static class SaveRequest {
        private String name;
        private String goal;
    }

    @Data
    public static class CompleteRequest {
        private Boolean retainOpenApplications;
    }

    @Data
    public static class CampaignView {
        private Long id;
        private Long userId;
        private String name;
        private String goal;
        private String status;
        private int applicationCount;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime archivedAt;
        private Integer lockVersion;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
