package com.codecoachai.ai.agent.campaignpulse.mapper;

import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulse;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CampaignPulseMapper {

    @Insert("""
            INSERT INTO career_campaign_pulse (
                user_id, campaign_id, snapshot_version, lock_version,
                created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{campaignId}, 0, 1,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int ensure(@Param("userId") Long userId, @Param("campaignId") Long campaignId);

    @Select("""
            SELECT *
            FROM career_campaign_pulse
            WHERE user_id = #{userId} AND campaign_id = #{campaignId} AND deleted = 0
            LIMIT 1
            """)
    CampaignPulse selectOwned(@Param("userId") Long userId, @Param("campaignId") Long campaignId);

    @Select("""
            SELECT *
            FROM career_campaign_pulse
            WHERE user_id = #{userId} AND campaign_id = #{campaignId} AND deleted = 0
            LIMIT 1 FOR UPDATE
            """)
    CampaignPulse selectOwnedForUpdate(
            @Param("userId") Long userId, @Param("campaignId") Long campaignId);

    @Update("""
            UPDATE career_campaign_pulse
            SET generation_claim_token = #{claimToken},
                generation_claim_fingerprint = #{fingerprint},
                generation_claimed_at = #{claimedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{pulseId} AND user_id = #{userId} AND deleted = 0
              AND (
                  generation_claim_token IS NULL
                  OR generation_claimed_at IS NULL
                  OR generation_claimed_at < #{expiredBefore}
              )
            """)
    int claim(@Param("userId") Long userId,
              @Param("pulseId") Long pulseId,
              @Param("claimToken") String claimToken,
              @Param("fingerprint") String fingerprint,
              @Param("claimedAt") LocalDateTime claimedAt,
              @Param("expiredBefore") LocalDateTime expiredBefore);

    @Update("""
            UPDATE career_campaign_pulse
            SET generation_claim_token = NULL,
                generation_claim_fingerprint = NULL,
                generation_claimed_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{pulseId} AND user_id = #{userId}
              AND generation_claim_token = #{claimToken} AND deleted = 0
            """)
    int release(@Param("userId") Long userId,
                @Param("pulseId") Long pulseId,
                @Param("claimToken") String claimToken);

    @Update("""
            UPDATE career_campaign_pulse
            SET current_snapshot_id = #{snapshotId},
                snapshot_version = #{snapshotVersion},
                last_generated_at = #{generatedAt},
                generation_claim_token = NULL,
                generation_claim_fingerprint = NULL,
                generation_claimed_at = NULL,
                lock_version = lock_version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{pulseId} AND user_id = #{userId}
              AND generation_claim_token = #{claimToken} AND deleted = 0
            """)
    int publish(@Param("userId") Long userId,
                @Param("pulseId") Long pulseId,
                @Param("snapshotId") Long snapshotId,
                @Param("snapshotVersion") Integer snapshotVersion,
                @Param("generatedAt") LocalDateTime generatedAt,
                @Param("claimToken") String claimToken);
}
