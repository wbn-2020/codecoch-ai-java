package com.codecoachai.resume.careercampaign;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerCampaignMapper extends BaseMapper<CareerCampaign> {

    @Select("""
            SELECT COUNT(1)
              FROM career_campaign
             WHERE user_id = #{userId}
               AND status = 'ACTIVE'
               AND deleted = 0
            """)
    int countActive(@Param("userId") Long userId);

    @Update("""
            UPDATE career_campaign
               SET status = #{nextStatus},
                   started_at = CASE WHEN #{nextStatus} = 'ACTIVE'
                                     THEN COALESCE(started_at, CURRENT_TIMESTAMP) ELSE started_at END,
                   completed_at = CASE WHEN #{nextStatus} = 'COMPLETED'
                                       THEN CURRENT_TIMESTAMP ELSE completed_at END,
                   archived_at = CASE WHEN #{nextStatus} = 'ARCHIVED'
                                      THEN CURRENT_TIMESTAMP ELSE archived_at END,
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{campaignId}
               AND user_id = #{userId}
               AND status = #{expectedStatus}
               AND lock_version = #{expectedLockVersion}
               AND deleted = 0
            """)
    int transition(@Param("campaignId") Long campaignId,
                   @Param("userId") Long userId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus,
                   @Param("expectedLockVersion") Integer expectedLockVersion);
}
