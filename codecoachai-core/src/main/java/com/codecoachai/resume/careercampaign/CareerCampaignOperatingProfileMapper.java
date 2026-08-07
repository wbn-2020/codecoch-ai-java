package com.codecoachai.resume.careercampaign;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerCampaignOperatingProfileMapper
        extends BaseMapper<CareerCampaignOperatingProfile> {

    @Select("""
            SELECT *
              FROM career_campaign_operating_profile
             WHERE user_id = #{userId}
               AND campaign_id = #{campaignId}
               AND deleted = 0
             LIMIT 1
            """)
    CareerCampaignOperatingProfile selectActive(@Param("userId") Long userId,
                                                 @Param("campaignId") Long campaignId);

    @Update("""
            UPDATE career_campaign_operating_profile
               SET weekly_application_target = #{weeklyApplicationTarget},
                   weekly_time_budget_minutes = #{weeklyTimeBudgetMinutes},
                   max_active_opportunities = #{maxActiveOpportunities},
                   stale_after_days = #{staleAfterDays},
                   default_follow_up_days = #{defaultFollowUpDays},
                   focus_roles_json = #{focusRolesJson},
                   focus_locations_json = #{focusLocationsJson},
                   focus_channels_json = #{focusChannelsJson},
                   timezone = #{timezone},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND user_id = #{userId}
               AND campaign_id = #{campaignId}
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int updateOptimistic(@Param("id") Long id,
                         @Param("userId") Long userId,
                         @Param("campaignId") Long campaignId,
                         @Param("expectedLockVersion") Integer expectedLockVersion,
                         @Param("weeklyApplicationTarget") Integer weeklyApplicationTarget,
                         @Param("weeklyTimeBudgetMinutes") Integer weeklyTimeBudgetMinutes,
                         @Param("maxActiveOpportunities") Integer maxActiveOpportunities,
                         @Param("staleAfterDays") Integer staleAfterDays,
                         @Param("defaultFollowUpDays") Integer defaultFollowUpDays,
                         @Param("focusRolesJson") String focusRolesJson,
                         @Param("focusLocationsJson") String focusLocationsJson,
                         @Param("focusChannelsJson") String focusChannelsJson,
                         @Param("timezone") String timezone);
}
