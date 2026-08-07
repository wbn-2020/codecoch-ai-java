package com.codecoachai.resume.careercalendar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Data;

public final class CareerCalendarModels {

    private CareerCalendarModels() {
    }

    @Data
    public static class EventSave {
        private Long applicationId;
        @NotBlank
        private String title;
        private String eventType = "FOLLOW_UP";
        @NotNull
        private LocalDateTime startsAt;
        @NotNull
        private LocalDateTime endsAt;
        @NotBlank
        private String timezone;
        private Boolean allDay = false;
        private String location;
        private String description;
        private String status = "CONFIRMED";
    }

    @Data
    public static class EventView {
        private Long id;
        private Long applicationId;
        private String title;
        private String eventType;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Instant startsAtUtc;
        private Instant endsAtUtc;
        private String timezone;
        private Boolean allDay;
        private String location;
        private String description;
        private String status;
        private String sourceType;
        private String sourceRef;
        private String externalUid;
        private Long importBatchId;
        private String preparationStatus;
        private Long preparationAiCallLogId;
        private LocalDateTime preparationGeneratedAt;
        private String preparationSourceHash;
        private Boolean preparationStale;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    public record ImportedEvent(Long applicationId, String title, String eventType,
                                LocalDateTime startsAt, LocalDateTime endsAt, String timezone,
                                boolean allDay, String location, String description, String status,
                                String sourceType, String sourceRef, String externalUid, Long importBatchId) {
    }
}
