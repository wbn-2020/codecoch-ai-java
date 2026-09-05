package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResult;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerEvidenceUsageResultMapper extends BaseMapper<CareerEvidenceUsageResult> {

    @Select("""
            SELECT *
              FROM career_evidence_usage_result
             WHERE id = #{resultId}
               AND user_id = #{userId}
               AND deleted = 0
             LIMIT 1
            """)
    CareerEvidenceUsageResult selectOwned(@Param("resultId") Long resultId,
                                          @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM career_evidence_usage_result
             WHERE user_id = #{userId}
               AND usage_id = #{usageId}
               AND event_key_hash = #{eventKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerEvidenceUsageResult selectByEventKey(@Param("userId") Long userId,
                                               @Param("usageId") Long usageId,
                                               @Param("eventKeyHash") String eventKeyHash);

    @Select("""
            SELECT *
              FROM career_evidence_usage_result
             WHERE id = #{resultId}
               AND user_id = #{userId}
               AND deleted = 0
             LIMIT 1 FOR UPDATE
            """)
    CareerEvidenceUsageResult selectForUpdate(@Param("resultId") Long resultId,
                                              @Param("userId") Long userId);

    @Update("""
            UPDATE career_evidence_usage_result
               SET current_snapshot_id = #{snapshotId},
                   snapshot_version = #{snapshotVersion},
                   status = #{status},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{resultId}
               AND user_id = #{userId}
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int updateCurrentSnapshot(@Param("resultId") Long resultId,
                              @Param("userId") Long userId,
                              @Param("snapshotId") Long snapshotId,
                              @Param("snapshotVersion") Integer snapshotVersion,
                              @Param("status") String status,
                              @Param("expectedLockVersion") Integer expectedLockVersion);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT r.usage_id)
              FROM career_evidence_usage_result r
              JOIN career_evidence_usage u
                ON u.id = r.usage_id
               AND u.user_id = r.user_id
               AND u.deleted = 0
             WHERE r.user_id = #{userId}
               AND r.deleted = 0
               <if test="campaignId != null">
                 AND u.campaign_id = #{campaignId}
               </if>
               <if test="applicationId != null">
                 AND u.application_id = #{applicationId}
               </if>
               <if test="assetType != null and assetType != ''">
                 AND u.asset_type = #{assetType}
               </if>
            </script>
            """)
    long selectCountByUsageScope(@Param("userId") Long userId,
                                 @Param("campaignId") Long campaignId,
                                 @Param("applicationId") Long applicationId,
                                 @Param("assetType") String assetType);

    @Select("""
            SELECT COUNT(DISTINCT r.usage_id)
              FROM career_evidence_usage_result r
              JOIN career_evidence_usage u
                ON u.id = r.usage_id
               AND u.user_id = r.user_id
               AND u.deleted = 0
              JOIN career_evidence_usage_result_snapshot s
                ON s.id = r.current_snapshot_id
             WHERE r.user_id = #{userId}
               AND r.deleted = 0
               AND r.status IN ('CONFIRMED', 'CORRECTED')
               AND s.outcome_code = #{outcomeCode}
               AND u.target_job_id = #{targetJobId}
               AND u.asset_type = #{assetType}
               AND u.asset_id = #{assetId}
            """)
    long countTrustedOutcomeByAsset(@Param("userId") Long userId,
                                    @Param("targetJobId") Long targetJobId,
                                    @Param("assetType") String assetType,
                                    @Param("assetId") Long assetId,
                                    @Param("outcomeCode") String outcomeCode);

    @Select("""
            SELECT DISTINCT r.usage_id
              FROM career_evidence_usage_result r
              JOIN career_evidence_usage u
                ON u.id = r.usage_id
               AND u.user_id = r.user_id
               AND u.deleted = 0
              JOIN career_evidence_usage_result_snapshot s
                ON s.id = r.current_snapshot_id
             WHERE r.user_id = #{userId}
               AND r.deleted = 0
               AND r.status IN ('CONFIRMED', 'CORRECTED')
               AND s.outcome_code = #{outcomeCode}
               AND u.target_job_id = #{targetJobId}
               AND u.asset_type = #{assetType}
               AND u.asset_id = #{assetId}
             ORDER BY r.usage_id
             LIMIT 8
            """)
    List<Long> selectTrustedOutcomeUsageIds(@Param("userId") Long userId,
                                            @Param("targetJobId") Long targetJobId,
                                            @Param("assetType") String assetType,
                                            @Param("assetId") Long assetId,
                                            @Param("outcomeCode") String outcomeCode);

}
