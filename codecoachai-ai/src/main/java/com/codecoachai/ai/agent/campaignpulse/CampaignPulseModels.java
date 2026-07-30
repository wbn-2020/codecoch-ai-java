package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

public final class CampaignPulseModels {

    private CampaignPulseModels() {
    }

    @Data
    public static class GenerateRequest {
        @NotNull
        private Long campaignId;
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;
        private String requestId;
    }

    @Data
    public static class PlanPreviewRequest {
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;
        private Integer maxTotalMinutes = 120;
        private List<String> selectedSemanticKeys = new ArrayList<>();
    }

    @Data
    public static class PulseView {
        private Long pulseId;
        private Long snapshotId;
        private Long campaignId;
        private Integer snapshotVersion;
        private LocalDateTime dataCutoffAt;
        private String inputHash;
        private Map<String, Object> facts = new LinkedHashMap<>();
        private Map<String, Object> metrics = new LinkedHashMap<>();
        private List<String> changes = new ArrayList<>();
        private List<String> driftSignals = new ArrayList<>();
        private List<String> limits = new ArrayList<>();
        private List<ActionItem> actionSeeds = new ArrayList<>();
        private Narrative narrative = new Narrative();
        private List<EvidenceRef> sources = new ArrayList<>();
        private String confidenceLevel;
        private Boolean fallback = false;
        private Long aiCallLogId;
        private LocalDateTime createdAt;
    }

    @Data
    public static class Narrative {
        private String summary;
        private List<String> facts = new ArrayList<>();
        private List<String> changes = new ArrayList<>();
        private List<String> driftReasons = new ArrayList<>();
        private List<String> focusAreas = new ArrayList<>();
        private List<String> actionSelections = new ArrayList<>();
        private List<String> limits = new ArrayList<>();
        private String confidenceLevel;
        private Boolean fallback = false;
        private String fallbackReason;
        private Long aiCallLogId;
    }

    @Data
    public static class Computation {
        private String inputHash;
        private Map<String, Object> facts = new LinkedHashMap<>();
        private Map<String, Object> metrics = new LinkedHashMap<>();
        private List<String> changes = new ArrayList<>();
        private List<String> driftSignals = new ArrayList<>();
        private List<String> limits = new ArrayList<>();
        private List<ActionItem> actionSeeds = new ArrayList<>();
        private List<EvidenceRef> sources = new ArrayList<>();
        private String confidenceLevel;
        private Boolean fallback = false;
        private LocalDateTime dataCutoffAt;
    }

    @Data
    public static class HistoryView {
        private Long campaignId;
        private List<PulseView> snapshots = new ArrayList<>();
    }
}
