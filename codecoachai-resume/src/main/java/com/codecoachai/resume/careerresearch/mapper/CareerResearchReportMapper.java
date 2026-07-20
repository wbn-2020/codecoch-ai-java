package com.codecoachai.resume.careerresearch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careerresearch.entity.CareerResearchReport;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerResearchReportMapper extends BaseMapper<CareerResearchReport> {
    @Update("""
            UPDATE career_research_report
               SET generation_status = 'GENERATING',
                   generation_claim_token = #{claimToken},
                   generation_claimed_at = #{claimedAt},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{reportId} AND user_id = #{userId} AND deleted = 0
               AND (generation_status <> 'GENERATING'
                    OR generation_claimed_at IS NULL
                    OR generation_claimed_at < #{expiredBefore})
            """)
    int claimGeneration(@Param("reportId") Long reportId,
                        @Param("userId") Long userId,
                        @Param("claimToken") String claimToken,
                        @Param("claimedAt") LocalDateTime claimedAt,
                        @Param("expiredBefore") LocalDateTime expiredBefore);

    @Update("""
            UPDATE career_research_report
               SET current_snapshot_id = #{snapshotId},
                   generation_status = 'READY',
                   generation_claim_token = NULL,
                   generation_claimed_at = NULL,
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{reportId} AND user_id = #{userId}
               AND generation_status = 'GENERATING'
               AND generation_claim_token = #{claimToken}
               AND deleted = 0
            """)
    int completeGeneration(@Param("reportId") Long reportId,
                           @Param("userId") Long userId,
                           @Param("claimToken") String claimToken,
                           @Param("snapshotId") Long snapshotId);

    @Update("""
            UPDATE career_research_report
               SET generation_status = 'FAILED',
                   generation_claim_token = NULL,
                   generation_claimed_at = NULL,
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{reportId} AND user_id = #{userId}
               AND generation_claim_token = #{claimToken}
               AND deleted = 0
            """)
    int releaseFailedGeneration(@Param("reportId") Long reportId,
                                @Param("userId") Long userId,
                                @Param("claimToken") String claimToken);
}
