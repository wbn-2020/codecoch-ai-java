package com.codecoachai.ai.agent.campaignreview.mapper;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerCampaignReviewMemoryCandidateMapper {

    @Insert("""
            INSERT INTO career_campaign_review_memory_candidate (
                user_id, review_id, snapshot_id, candidate_scope_type, candidate_scope_key,
                candidate_type, usage_source_hash, evidence_count, sample_count, limits_json,
                candidate_key, semantic_hash, title, content, source_ref, confidence_level,
                status, validity_days, expires_at,
                created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{reviewId}, #{snapshotId}, #{candidateScopeType}, #{candidateScopeKey},
                #{candidateType}, #{usageSourceHash}, #{evidenceCount}, #{sampleCount}, #{limitsJson},
                #{candidateKey}, #{semanticHash}, #{title}, #{content}, #{sourceRef}, #{confidenceLevel},
                COALESCE(#{status}, 'PENDING_CONFIRMATION'), #{validityDays}, #{expiresAt},
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertCandidate(CareerCampaignReviewMemoryCandidate candidate);

    @Select("""
            SELECT *
            FROM career_campaign_review_memory_candidate
            WHERE id = #{candidateId} AND user_id = #{userId} AND deleted = 0
            LIMIT 1 FOR UPDATE
            """)
    CareerCampaignReviewMemoryCandidate selectOwnedForUpdate(
            @Param("userId") Long userId, @Param("candidateId") Long candidateId);

    @Select("""
            SELECT *
            FROM career_campaign_review_memory_candidate
            WHERE id = #{candidateId} AND user_id = #{userId} AND deleted = 0
            LIMIT 1
            """)
    CareerCampaignReviewMemoryCandidate selectOwned(
            @Param("userId") Long userId, @Param("candidateId") Long candidateId);

    @Select("""
            SELECT *
            FROM career_campaign_review_memory_candidate
            WHERE user_id = #{userId} AND snapshot_id = #{snapshotId} AND deleted = 0
            ORDER BY id
            """)
    List<CareerCampaignReviewMemoryCandidate> selectBySnapshot(
            @Param("userId") Long userId, @Param("snapshotId") Long snapshotId);

    @Select("""
            <script>
            SELECT *
            FROM career_campaign_review_memory_candidate
            WHERE user_id = #{userId} AND deleted = 0
              <if test="scopeType != null and scopeType != ''">
                AND candidate_scope_type = #{scopeType}
              </if>
              <if test="scopeKey != null and scopeKey != ''">
                AND candidate_scope_key = #{scopeKey}
              </if>
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
            ORDER BY updated_at DESC, id DESC
            LIMIT 100
            </script>
            """)
    List<CareerCampaignReviewMemoryCandidate> selectByScope(
            @Param("userId") Long userId,
            @Param("scopeType") String scopeType,
            @Param("scopeKey") String scopeKey,
            @Param("status") String status);

    @Update("""
            UPDATE career_campaign_review_memory_candidate
            SET status = #{status},
                confirmed_at = #{confirmedAt},
                decision_idempotency_key_hash = #{idempotencyKeyHash},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{candidateId} AND user_id = #{userId}
              AND status IN ('PENDING', 'PENDING_CONFIRMATION') AND deleted = 0
            """)
    int decide(@Param("userId") Long userId,
               @Param("candidateId") Long candidateId,
               @Param("status") String status,
               @Param("idempotencyKeyHash") String idempotencyKeyHash,
               @Param("confirmedAt") LocalDateTime confirmedAt);

    @Update("""
            UPDATE career_campaign_review_memory_candidate
            SET status = #{status},
                content = #{content},
                 decision_code = #{decisionCode},
                 decision_payload_hash = #{payloadHash},
                 decision_history_json = #{decisionHistoryJson},
                 decision_idempotency_key_hash = #{idempotencyKeyHash},
                decision_at = #{decisionAt},
                confirmed_at = #{confirmedAt},
                expires_at = #{expiresAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{candidateId} AND user_id = #{userId}
              AND status IN ('PENDING', 'PENDING_CONFIRMATION', 'WEAK_OBSERVATION')
              AND deleted = 0
            """)
    int decideV9(@Param("userId") Long userId,
                 @Param("candidateId") Long candidateId,
                 @Param("status") String status,
                 @Param("content") String content,
                 @Param("decisionCode") String decisionCode,
                 @Param("payloadHash") String payloadHash,
                 @Param("decisionHistoryJson") String decisionHistoryJson,
                 @Param("idempotencyKeyHash") String idempotencyKeyHash,
                 @Param("decisionAt") LocalDateTime decisionAt,
                 @Param("confirmedAt") LocalDateTime confirmedAt,
                 @Param("expiresAt") LocalDateTime expiresAt);

    @Update("""
            UPDATE career_campaign_review_memory_candidate
            SET promoted_memory_id = #{memoryId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{candidateId} AND user_id = #{userId}
              AND deleted = 0
            """)
    int updatePromotedMemory(@Param("userId") Long userId,
                             @Param("candidateId") Long candidateId,
                             @Param("memoryId") Long memoryId);

    @Update("""
            UPDATE career_campaign_review_memory_candidate
            SET status = 'EXPIRED',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{candidateId} AND user_id = #{userId}
              AND status IN ('PENDING', 'PENDING_CONFIRMATION', 'WEAK_OBSERVATION')
              AND expires_at IS NOT NULL AND expires_at &lt;= #{now}
              AND deleted = 0
            """)
    int expire(@Param("userId") Long userId,
               @Param("candidateId") Long candidateId,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE career_campaign_review_memory_candidate
            SET status = 'EXPIRED',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{candidateId} AND user_id = #{userId}
              AND candidate_type = 'EVIDENCE_REUSE'
              AND status IN (
                  'PENDING', 'PENDING_CONFIRMATION', 'WEAK_OBSERVATION',
                  'CONFIRMED', 'CONFIRMED_BY_USER'
              )
              AND deleted = 0
            """)
    int expireForFactsChange(@Param("userId") Long userId,
                             @Param("candidateId") Long candidateId);
}
