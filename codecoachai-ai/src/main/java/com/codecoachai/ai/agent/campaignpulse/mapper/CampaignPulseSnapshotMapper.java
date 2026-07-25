package com.codecoachai.ai.agent.campaignpulse.mapper;

import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulseSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CampaignPulseSnapshotMapper {

    @Insert("""
            INSERT INTO career_campaign_pulse_snapshot (
                user_id, pulse_id, campaign_id, snapshot_version, data_cutoff_at,
                input_hash, generation_fingerprint, idempotency_key_hash,
                idempotency_payload_hash, facts_json, metrics_json, changes_json,
                drift_signals_json, limits_json, action_seeds_json, narrative_json,
                confidence_level, fallback, ai_call_log_id, created_at, deleted
            ) VALUES (
                #{userId}, #{pulseId}, #{campaignId}, #{snapshotVersion}, #{dataCutoffAt},
                #{inputHash}, #{generationFingerprint}, #{idempotencyKeyHash},
                #{idempotencyPayloadHash}, #{factsJson}, #{metricsJson}, #{changesJson},
                #{driftSignalsJson}, #{limitsJson}, #{actionSeedsJson}, #{narrativeJson},
                #{confidenceLevel}, #{fallback}, #{aiCallLogId}, CURRENT_TIMESTAMP, 0
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CampaignPulseSnapshot snapshot);

    @Select("""
            SELECT *
            FROM career_campaign_pulse_snapshot
            WHERE user_id = #{userId} AND campaign_id = #{campaignId}
              AND generation_fingerprint = #{fingerprint} AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    CampaignPulseSnapshot selectByFingerprint(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("fingerprint") String fingerprint);

    @Select("""
            SELECT *
            FROM career_campaign_pulse_snapshot
            WHERE user_id = #{userId}
              AND idempotency_key_hash = #{idempotencyKeyHash}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    CampaignPulseSnapshot selectByIdempotency(
            @Param("userId") Long userId,
            @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT *
            FROM career_campaign_pulse_snapshot
            WHERE id = #{snapshotId} AND user_id = #{userId} AND deleted = 0
            LIMIT 1
            """)
    CampaignPulseSnapshot selectOwned(
            @Param("userId") Long userId, @Param("snapshotId") Long snapshotId);

    @Select("""
            SELECT s.*
            FROM career_campaign_pulse_snapshot s
            JOIN career_campaign_pulse p ON p.current_snapshot_id = s.id
            WHERE p.user_id = #{userId} AND p.campaign_id = #{campaignId}
              AND p.deleted = 0 AND s.deleted = 0
            LIMIT 1
            """)
    CampaignPulseSnapshot selectCurrent(
            @Param("userId") Long userId, @Param("campaignId") Long campaignId);

    @Select("""
            SELECT *
            FROM career_campaign_pulse_snapshot
            WHERE user_id = #{userId} AND campaign_id = #{campaignId} AND deleted = 0
            ORDER BY snapshot_version DESC, id DESC
            LIMIT #{limit}
            """)
    List<CampaignPulseSnapshot> selectHistory(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("limit") Integer limit);
}
