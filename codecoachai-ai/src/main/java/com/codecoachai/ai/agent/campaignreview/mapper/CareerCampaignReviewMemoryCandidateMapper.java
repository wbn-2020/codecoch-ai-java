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
                user_id, review_id, snapshot_id, candidate_key, semantic_hash, title, content,
                source_ref, confidence_level, status, validity_days, expires_at,
                created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{reviewId}, #{snapshotId}, #{candidateKey}, #{semanticHash}, #{title}, #{content},
                #{sourceRef}, #{confidenceLevel}, 'PENDING', #{validityDays}, #{expiresAt},
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
            WHERE user_id = #{userId} AND snapshot_id = #{snapshotId} AND deleted = 0
            ORDER BY id
            """)
    List<CareerCampaignReviewMemoryCandidate> selectBySnapshot(
            @Param("userId") Long userId, @Param("snapshotId") Long snapshotId);

    @Update("""
            UPDATE career_campaign_review_memory_candidate
            SET status = #{status},
                confirmed_at = #{confirmedAt},
                decision_idempotency_key_hash = #{idempotencyKeyHash},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{candidateId} AND user_id = #{userId}
              AND status = 'PENDING' AND deleted = 0
            """)
    int decide(@Param("userId") Long userId,
               @Param("candidateId") Long candidateId,
               @Param("status") String status,
               @Param("idempotencyKeyHash") String idempotencyKeyHash,
               @Param("confirmedAt") LocalDateTime confirmedAt);
}
