package com.codecoachai.ai.agent.campaignreview.mapper;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerCampaignReviewSnapshotMapper {

    @Insert("""
            INSERT INTO career_campaign_review_snapshot (
                user_id, review_id, campaign_id, snapshot_version,
                data_cutoff_at, input_hash, generation_fingerprint,
                idempotency_key_hash, idempotency_payload_hash,
                facts_json, coverage_json, limits_json, signals_json,
                memory_candidates_json, experiment_candidates_json, next_cycle_actions_json,
                summary, confidence_level, result_source, fallback, fallback_reason,
                ai_call_log_id, created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{reviewId}, #{campaignId}, #{snapshotVersion},
                #{dataCutoffAt}, #{inputHash}, #{generationFingerprint},
                #{idempotencyKeyHash}, #{idempotencyPayloadHash},
                #{factsJson}, #{coverageJson}, #{limitsJson}, #{signalsJson},
                #{memoryCandidatesJson}, #{experimentCandidatesJson}, #{nextCycleActionsJson},
                #{summary}, #{confidenceLevel}, #{resultSource}, #{fallback}, #{fallbackReason},
                #{aiCallLogId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            """)
    int insertSnapshot(CareerCampaignReviewSnapshot snapshot);

    @Select("""
            SELECT *
            FROM career_campaign_review_snapshot
            WHERE user_id = #{userId}
              AND review_id = #{reviewId}
              AND idempotency_key_hash = #{idempotencyKeyHash}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    CareerCampaignReviewSnapshot selectByIdempotency(
            @Param("userId") Long userId,
            @Param("reviewId") Long reviewId,
            @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT *
            FROM career_campaign_review_snapshot
            WHERE id = #{snapshotId}
              AND user_id = #{userId}
              AND review_id = #{reviewId}
              AND deleted = 0
            LIMIT 1
            """)
    CareerCampaignReviewSnapshot selectOwned(@Param("userId") Long userId,
                                             @Param("reviewId") Long reviewId,
                                             @Param("snapshotId") Long snapshotId);

    @Select("""
            SELECT *
            FROM career_campaign_review_snapshot
            WHERE id = #{snapshotId} AND user_id = #{userId} AND deleted = 0
            LIMIT 1
            """)
    CareerCampaignReviewSnapshot selectById(@Param("userId") Long userId,
                                            @Param("snapshotId") Long snapshotId);
}
