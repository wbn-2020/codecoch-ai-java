package com.codecoachai.resume.careerimport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

public final class CareerImportModels {

    private CareerImportModels() {
    }

    @Data
    public static class ImportPreview {
        private String format;
        private String timezone;
        private List<String> headers = new ArrayList<>();
        private Map<String, String> suggestedMapping = new LinkedHashMap<>();
        private List<String> supportedFields = new ArrayList<>();
        private Integer totalCount;
        private Integer validCount;
        private Integer errorCount;
        private Integer duplicateCount;
        private List<ImportRowView> rows = new ArrayList<>();
    }

    @Data
    public static class ImportResult {
        private Long batchId;
        private String format;
        private String status;
        private Integer totalCount;
        private Integer successCount;
        private Integer errorCount;
        private Integer duplicateCount;
        private List<ImportRowView> rows = new ArrayList<>();
    }

    @Data
    public static class ImportRowView {
        private Integer rowNumber;
        private String disposition;
        private String errorCode;
        private String errorMessage;
        private Long applicationId;
        private Long calendarEventId;
        private Map<String, String> raw = new LinkedHashMap<>();
        private List<DuplicateCandidate> duplicateCandidates = new ArrayList<>();
    }

    @Data
    public static class DuplicateCandidate {
        private Long applicationId;
        private String companyName;
        private String jobTitle;
        private LocalDateTime appliedAt;
        private String reason;
    }
}
