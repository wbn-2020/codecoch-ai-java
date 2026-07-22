package com.codecoachai.ai.agent.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InnerCampaignArchiveSourceMapper {

    @Select("""
            SELECT review.id AS reviewId,
                   snapshot.id AS snapshotId,
                   review.campaign_id AS campaignId,
                   snapshot.snapshot_version AS snapshotVersion,
                   review.review_status AS reviewStatus,
                   snapshot.data_cutoff_at AS dataCutoffAt,
                   snapshot.summary AS summary,
                   snapshot.confidence_level AS confidenceLevel,
                   snapshot.result_source AS resultSource,
                   snapshot.fallback AS fallback,
                   snapshot.fallback_reason AS fallbackReason,
                   snapshot.facts_json AS factsJson,
                   snapshot.coverage_json AS coverageJson,
                   snapshot.limits_json AS limitsJson,
                   snapshot.signals_json AS signalsJson,
                   snapshot.memory_candidates_json AS memoryCandidatesJson,
                   snapshot.experiment_candidates_json AS experimentCandidatesJson,
                   snapshot.next_cycle_actions_json AS nextCycleActionsJson
              FROM career_campaign_review review
              JOIN career_campaign_review_snapshot snapshot
                ON snapshot.review_id = review.id
               AND snapshot.user_id = #{userId}
               AND snapshot.campaign_id = #{campaignId}
               AND snapshot.deleted = 0
             WHERE review.user_id = #{userId}
               AND review.campaign_id = #{campaignId}
               AND review.deleted = 0
               AND snapshot.data_cutoff_at <= #{dataCutoffAt}
             ORDER BY snapshot.data_cutoff_at DESC, snapshot.id DESC
             LIMIT 1
            """)
    ReviewRow selectReview(@Param("userId") Long userId,
                           @Param("campaignId") Long campaignId,
                           @Param("dataCutoffAt") LocalDateTime dataCutoffAt);

    @Select("""
            SELECT snapshot.id AS snapshotId,
                   pulse.campaign_id AS campaignId,
                   snapshot.pulse_id AS pulseId,
                   snapshot.snapshot_version AS snapshotVersion,
                   snapshot.data_cutoff_at AS dataCutoffAt,
                   snapshot.input_hash AS inputHash,
                   snapshot.confidence_level AS confidenceLevel,
                   snapshot.fallback AS fallback,
                   snapshot.facts_json AS factsJson,
                   snapshot.metrics_json AS metricsJson,
                   snapshot.changes_json AS changesJson,
                   snapshot.drift_signals_json AS driftSignalsJson,
                   snapshot.limits_json AS limitsJson,
                   snapshot.action_seeds_json AS actionSeedsJson,
                   snapshot.narrative_json AS narrativeJson
              FROM career_campaign_pulse_snapshot snapshot
              JOIN career_campaign_pulse pulse
                ON pulse.id = snapshot.pulse_id
               AND pulse.user_id = #{userId}
               AND pulse.campaign_id = #{campaignId}
             WHERE snapshot.data_cutoff_at <= #{dataCutoffAt}
               AND snapshot.user_id = #{userId}
               AND snapshot.campaign_id = #{campaignId}
               AND snapshot.deleted = 0
               AND pulse.deleted = 0
             ORDER BY snapshot.data_cutoff_at DESC, snapshot.id DESC
             LIMIT 100
            """)
    List<PulseRow> selectPulses(@Param("userId") Long userId,
                                @Param("campaignId") Long campaignId,
                                @Param("dataCutoffAt") LocalDateTime dataCutoffAt);

    @Data
    class ReviewRow {
        private Long reviewId;
        private Long snapshotId;
        private Long campaignId;
        private Integer snapshotVersion;
        private String reviewStatus;
        private LocalDateTime dataCutoffAt;
        private String summary;
        private String confidenceLevel;
        private String resultSource;
        private Integer fallback;
        private String fallbackReason;
        private String factsJson;
        private String coverageJson;
        private String limitsJson;
        private String signalsJson;
        private String memoryCandidatesJson;
        private String experimentCandidatesJson;
        private String nextCycleActionsJson;
    }

    @Data
    class PulseRow {
        private Long pulseId;
        private Long snapshotId;
        private Long campaignId;
        private Integer snapshotVersion;
        private LocalDateTime dataCutoffAt;
        private String inputHash;
        private String confidenceLevel;
        private Integer fallback;
        private String factsJson;
        private String metricsJson;
        private String changesJson;
        private String driftSignalsJson;
        private String limitsJson;
        private String actionSeedsJson;
        private String narrativeJson;
    }
}
