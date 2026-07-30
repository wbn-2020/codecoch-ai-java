package com.codecoachai.ai.agent.campaigncockpit.mapper;

import com.codecoachai.ai.agent.campaigncockpit.domain.entity.CampaignActionDecision;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CampaignActionDecisionMapper {

    @Insert("""
            INSERT INTO career_campaign_action_decision (
                user_id, campaign_id, semantic_key, source_hash, action_type,
                decision_status, snoozed_until, reason, idempotency_key_hash,
                payload_hash, decided_at, active_guard, created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{campaignId}, #{semanticKey}, #{sourceHash}, #{actionType},
                #{decisionStatus}, #{snoozedUntil}, #{reason}, #{idempotencyKeyHash},
                #{payloadHash}, #{decidedAt}, #{activeGuard}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CampaignActionDecision decision);

    @Select("""
            SELECT *
            FROM career_campaign_action_decision
            WHERE user_id = #{userId}
              AND campaign_id = #{campaignId}
              AND semantic_key = #{semanticKey}
              AND source_hash = #{sourceHash}
              AND active_guard = 1
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    CampaignActionDecision selectBySemanticSource(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("semanticKey") String semanticKey,
            @Param("sourceHash") String sourceHash);

    @Select("""
            SELECT *
            FROM career_campaign_action_decision
            WHERE user_id = #{userId}
              AND idempotency_key_hash = #{idempotencyKeyHash}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    CampaignActionDecision selectByIdempotency(
            @Param("userId") Long userId,
            @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT *
            FROM career_campaign_action_decision
            WHERE user_id = #{userId}
              AND campaign_id = #{campaignId}
              AND deleted = 0
            ORDER BY decided_at DESC, id DESC
            LIMIT 200
            """)
    List<CampaignActionDecision> selectByCampaign(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId);

    @Update("""
            UPDATE career_campaign_action_decision
            SET active_guard = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
              AND campaign_id = #{campaignId}
              AND semantic_key = #{semanticKey}
              AND source_hash = #{sourceHash}
              AND active_guard = 1
              AND deleted = 0
            """)
    int deactivateCurrent(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("semanticKey") String semanticKey,
            @Param("sourceHash") String sourceHash);
}
