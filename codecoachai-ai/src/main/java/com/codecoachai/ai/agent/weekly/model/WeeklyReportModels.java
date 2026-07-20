package com.codecoachai.ai.agent.weekly.model;

import com.codecoachai.ai.agent.domain.entity.AgentPlanAdjustment;
import com.codecoachai.ai.agent.domain.entity.AgentPlanInfluence;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.entity.ReadinessScoreRecord;
import com.codecoachai.ai.agent.domain.entity.SkillGrowthSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyExperimentSuggestionVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyPlanDraftVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportCoverageVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportFactVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportHypothesisVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportSignalVO;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public final class WeeklyReportModels {

    private WeeklyReportModels() {
    }

    @Data
    public static class RequestContext {

        private Long userId;
        private Long targetJobId;
        private String targetScopeKey;
        private LocalDate weekStartDate;
        private LocalDate weekEndDate;
        private String timezone;
        private ZoneId zoneId;
        private LocalDateTime rangeStartUtc;
        private LocalDateTime rangeEndUtc;
        private LocalDateTime databaseRangeStartAt;
        private LocalDateTime databaseRangeEndAt;
        private LocalDateTime sourceCutoffAt;
        private LocalDateTime databaseSourceCutoffAt;
        private LocalDateTime generatedAt;
        private String reportStatus;
        private String operation;
        private String requestId;
        private String idempotencyKeyHash;
        private String idempotencyPayloadHash;
        private String traceId;
        private Boolean forceRefresh;
    }

    @Data
    public static class QueryContext {

        private Long userId;
        private Long targetJobId;
        private String targetScopeKey;
        private LocalDate weekStartDate;
        private LocalDate fromWeekStart;
        private LocalDate toWeekStart;
        private String timezone;
        private ZoneId zoneId;
        private Integer limit;
    }

    @Data
    public static class EvidenceBundle {

        private AgentWeekPlan weekPlan;
        private List<AgentWeekPlanItem> weekPlanItems = new ArrayList<>();
        private List<AgentPlanAdjustment> adjustments = new ArrayList<>();
        private List<AgentPlanInfluence> influences = new ArrayList<>();
        private List<AgentReview> reviews = new ArrayList<>();
        private List<ReadinessScoreRecord> readinessRecords = new ArrayList<>();
        private List<SkillGrowthSnapshot> skillSnapshots = new ArrayList<>();
        private List<AgentWeeklyReportSnapshot> comparableSnapshots = new ArrayList<>();
        private WeeklyCareerEvidenceVO careerEvidence;
        private WeeklyInterviewEvidenceVO interviewEvidence;
        private Boolean careerAvailable = true;
        private Boolean interviewAvailable = true;
        private String careerFailureCode;
        private String interviewFailureCode;
        private Boolean localTruncated = false;
        private List<String> collectionWarnings = new ArrayList<>();
    }

    @Data
    public static class AggregationResult {

        private List<WeeklyReportFactVO> facts = new ArrayList<>();
        private List<WeeklyReportSignalVO> signals = new ArrayList<>();
        private List<WeeklyReportHypothesisVO> hypotheses = new ArrayList<>();
        private List<WeeklyExperimentSuggestionVO> experimentSuggestions = new ArrayList<>();
        private WeeklyPlanDraftVO planDraft;
        private WeeklyReportCoverageVO coverage;
        private List<AgentWeeklyReportSource> sources = new ArrayList<>();
        private List<String> limits = new ArrayList<>();
        private String confidenceLevel;
        private String ruleSummary;
        private String inputHash;
        private String generationFingerprint;
    }

    @Data
    public static class NarrativeResult {

        private String summary;
        private List<WeeklyReportHypothesisVO> hypotheses = new ArrayList<>();
        private String resultSource;
        private Boolean fallback;
        private String fallbackReason;
        private Long aiCallLogId;
        private String promptSchemaVersion;
    }

    @Data
    public static class SaveCommand {

        private RequestContext context;
        private AggregationResult aggregation;
        private NarrativeResult narrative;
    }

    @Data
    public static class StoredView {

        private AgentWeeklyReport report;
        private AgentWeeklyReportSnapshot snapshot;
        private List<AgentWeeklyReportSource> sources = new ArrayList<>();
        private List<AgentWeeklyReportSnapshot> history = new ArrayList<>();
        private String operationResult;
    }

    @Data
    public static class GenerationClaim {

        private boolean owner;
        private String claimToken;
        private StoredView replay;
    }
}
