package com.codecoachai.resume.careercampaign;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public final class CareerCampaignOperatingProfileModels {

    public static final int DEFAULT_WEEKLY_APPLICATION_TARGET = 3;
    public static final int DEFAULT_WEEKLY_TIME_BUDGET_MINUTES = 180;
    public static final int DEFAULT_MAX_ACTIVE_OPPORTUNITIES = 10;
    public static final int DEFAULT_STALE_AFTER_DAYS = 7;
    public static final int DEFAULT_FOLLOW_UP_DAYS = 5;
    public static final String DEFAULT_TIMEZONE = "UTC";

    private CareerCampaignOperatingProfileModels() {
    }

    public static OperatingProfileView conservativeDefaults(Long userId, Long campaignId) {
        OperatingProfileView view = new OperatingProfileView();
        view.setUserId(userId);
        view.setCampaignId(campaignId);
        view.setConfigured(false);
        view.setWeeklyApplicationTarget(DEFAULT_WEEKLY_APPLICATION_TARGET);
        view.setWeeklyTimeBudgetMinutes(DEFAULT_WEEKLY_TIME_BUDGET_MINUTES);
        view.setMaxActiveOpportunities(DEFAULT_MAX_ACTIVE_OPPORTUNITIES);
        view.setStaleAfterDays(DEFAULT_STALE_AFTER_DAYS);
        view.setDefaultFollowUpDays(DEFAULT_FOLLOW_UP_DAYS);
        view.setTimezone(DEFAULT_TIMEZONE);
        view.setLockVersion(0);
        return view;
    }

    @Data
    public static class SaveRequest {
        private Integer weeklyApplicationTarget;
        private Integer weeklyTimeBudgetMinutes;
        private Integer maxActiveOpportunities;
        private Integer staleAfterDays;
        private Integer defaultFollowUpDays;
        private List<String> focusRoles = new ArrayList<>();
        private List<String> focusLocations = new ArrayList<>();
        private List<String> focusChannels = new ArrayList<>();
        private String timezone;
        @JsonAlias({"lockVersion"})
        private Integer expectedLockVersion;
    }

    @Data
    public static class OperatingProfileView {
        private Long id;
        private Long userId;
        private Long campaignId;
        private Boolean configured;
        private Integer weeklyApplicationTarget;
        private Integer weeklyTimeBudgetMinutes;
        private Integer maxActiveOpportunities;
        private Integer staleAfterDays;
        private Integer defaultFollowUpDays;
        private List<String> focusRoles = new ArrayList<>();
        private List<String> focusLocations = new ArrayList<>();
        private List<String> focusChannels = new ArrayList<>();
        private String timezone;
        private Integer lockVersion;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
