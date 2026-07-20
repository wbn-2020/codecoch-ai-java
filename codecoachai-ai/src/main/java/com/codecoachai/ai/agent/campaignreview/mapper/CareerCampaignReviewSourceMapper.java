package com.codecoachai.ai.agent.campaignreview.mapper;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSource;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerCampaignReviewSourceMapper {

    @Insert("""
            INSERT INTO career_campaign_review_source (
                user_id, snapshot_id, source_type, source_id,
                source_time, source_updated_at, inclusion_status, exclude_reason,
                source_hash, safe_summary, metadata_json, created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{snapshotId}, #{sourceType}, #{sourceId},
                #{sourceTime}, #{sourceUpdatedAt}, #{inclusionStatus}, #{excludeReason},
                #{sourceHash}, #{safeSummary}, #{metadataJson}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            """)
    int insertSource(CareerCampaignReviewSource source);

    @Select("""
            SELECT *
            FROM career_campaign_review_source
            WHERE user_id = #{userId} AND snapshot_id = #{snapshotId} AND deleted = 0
            ORDER BY id
            """)
    List<CareerCampaignReviewSource> selectBySnapshot(@Param("userId") Long userId,
                                                      @Param("snapshotId") Long snapshotId);
}
