package com.codecoachai.resume.careercampaign;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public final class CareerCampaignModels {

    private CareerCampaignModels() {
    }

    @Data
    public static class SaveRequest {
        private String name;
        private String goal;
        private Integer expectedLockVersion;
        private String idempotencyKey;
        private String note;
    }

    @Data
    public static class CompleteRequest {
        private Boolean retainOpenApplications;
        private Integer expectedLockVersion;
        private String idempotencyKey;
        private String note;
    }

    @Data
    public static class ActionRequest {
        private Integer expectedLockVersion;
        private String idempotencyKey;
        private String note;
    }

    @Data
    public static class CampaignView {
        private Long id;
        private Long userId;
        private String name;
        private String goal;
        private String status;
        private int applicationCount;
        private List<String> allowedTransitions = new ArrayList<>();
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime archivedAt;
        private Integer lockVersion;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
