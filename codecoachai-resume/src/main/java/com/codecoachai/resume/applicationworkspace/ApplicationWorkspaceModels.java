package com.codecoachai.resume.applicationworkspace;

import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careercampaign.CareerCampaign;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

public final class ApplicationWorkspaceModels {

    private ApplicationWorkspaceModels() {
    }

    @Data
    public static class StatusTransitionRequest {
        private String targetStatus;
        private Integer expectedLockVersion;
        private String idempotencyKey;
    }

    @Data
    public static class WorkspaceView {
        private JobApplication application;
        private CareerCampaign campaign;
        private List<JobApplicationEvent> timeline = new ArrayList<>();
        private List<CareerCalendarEvent> calendar = new ArrayList<>();
        private List<JobApplicationPackage> materials = new ArrayList<>();
        private List<String> nextSteps = new ArrayList<>();
        private List<String> capabilities = new ArrayList<>();
        private Map<String, Coverage> coverage = new LinkedHashMap<>();
        private List<String> warnings = new ArrayList<>();
    }

    public record Coverage(String owner, boolean available, int itemCount, boolean truncated) {
    }
}
