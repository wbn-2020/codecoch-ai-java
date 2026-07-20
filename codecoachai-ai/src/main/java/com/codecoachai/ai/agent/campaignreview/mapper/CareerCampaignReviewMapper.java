package com.codecoachai.ai.agent.campaignreview.mapper;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReview;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerCampaignReviewMapper {

    @Insert("""
            INSERT INTO career_campaign_review (
                user_id, campaign_id, review_status, snapshot_version,
                created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{campaignId}, 'NOT_GENERATED', 0,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int ensureIdentity(@Param("userId") Long userId, @Param("campaignId") Long campaignId);

    @Select("""
            SELECT *
            FROM career_campaign_review
            WHERE id = #{reviewId} AND user_id = #{userId} AND deleted = 0
            LIMIT 1
            """)
    CareerCampaignReview selectOwned(@Param("userId") Long userId, @Param("reviewId") Long reviewId);

    @Select("""
            SELECT *
            FROM career_campaign_review
            WHERE campaign_id = #{campaignId} AND user_id = #{userId} AND deleted = 0
            LIMIT 1
            """)
    CareerCampaignReview selectOwnedByCampaign(@Param("userId") Long userId,
                                               @Param("campaignId") Long campaignId);

    @Select("""
            SELECT *
            FROM career_campaign_review
            WHERE user_id = #{userId} AND campaign_id = #{campaignId} AND deleted = 0
            LIMIT 1 FOR UPDATE
            """)
    CareerCampaignReview selectIdentityForUpdate(@Param("userId") Long userId,
                                                 @Param("campaignId") Long campaignId);

    @Update("""
            UPDATE career_campaign_review
            SET generation_claim_fingerprint = #{fingerprint},
                generation_claim_token = #{claimToken},
                generation_claim_idempotency_key_hash = #{idempotencyKeyHash},
                generation_claim_payload_hash = #{payloadHash},
                generation_claimed_at = #{claimedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{reviewId} AND user_id = #{userId} AND deleted = 0
              AND (
                  generation_claim_token IS NULL
                  OR generation_claimed_at IS NULL
                  OR generation_claimed_at &lt; #{expiredBefore}
              )
            """)
    int claimGeneration(@Param("userId") Long userId,
                        @Param("reviewId") Long reviewId,
                        @Param("fingerprint") String fingerprint,
                        @Param("claimToken") String claimToken,
                        @Param("idempotencyKeyHash") String idempotencyKeyHash,
                        @Param("payloadHash") String payloadHash,
                        @Param("claimedAt") LocalDateTime claimedAt,
                        @Param("expiredBefore") LocalDateTime expiredBefore);

    @Update("""
            UPDATE career_campaign_review
            SET generation_claim_fingerprint = NULL,
                generation_claim_token = NULL,
                generation_claim_idempotency_key_hash = NULL,
                generation_claim_payload_hash = NULL,
                generation_claimed_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{reviewId} AND user_id = #{userId}
              AND generation_claim_token = #{claimToken} AND deleted = 0
            """)
    int releaseClaim(@Param("userId") Long userId,
                     @Param("reviewId") Long reviewId,
                     @Param("claimToken") String claimToken);

    @Update("""
            UPDATE career_campaign_review
            SET current_snapshot_id = #{snapshotId},
                review_status = #{reviewStatus},
                snapshot_version = #{snapshotVersion},
                generation_claim_fingerprint = NULL,
                generation_claim_token = NULL,
                generation_claim_idempotency_key_hash = NULL,
                generation_claim_payload_hash = NULL,
                generation_claimed_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{reviewId} AND user_id = #{userId}
              AND generation_claim_token = #{claimToken} AND deleted = 0
            """)
    int publishSnapshot(@Param("userId") Long userId,
                        @Param("reviewId") Long reviewId,
                        @Param("snapshotId") Long snapshotId,
                        @Param("snapshotVersion") Integer snapshotVersion,
                        @Param("reviewStatus") String reviewStatus,
                        @Param("claimToken") String claimToken);
}
