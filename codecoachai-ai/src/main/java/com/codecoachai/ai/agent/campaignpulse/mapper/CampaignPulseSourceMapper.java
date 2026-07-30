package com.codecoachai.ai.agent.campaignpulse.mapper;

import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulseSource;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CampaignPulseSourceMapper {

    @Insert("""
            INSERT INTO career_campaign_pulse_source (
                user_id, snapshot_id, source_type, source_id, source_version,
                source_hash, application_id, campaign_id, observed_at,
                field_path, safe_summary, created_at, deleted
            ) VALUES (
                #{userId}, #{snapshotId}, #{sourceType}, #{sourceId}, #{sourceVersion},
                #{sourceHash}, #{applicationId}, #{campaignId}, #{observedAt},
                #{fieldPath}, #{safeSummary}, CURRENT_TIMESTAMP, 0
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CampaignPulseSource source);

    @Select("""
            SELECT *
            FROM career_campaign_pulse_source
            WHERE user_id = #{userId} AND snapshot_id = #{snapshotId} AND deleted = 0
            ORDER BY id ASC
            """)
    List<CampaignPulseSource> selectBySnapshot(
            @Param("userId") Long userId, @Param("snapshotId") Long snapshotId);
}
