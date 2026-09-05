package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAbilityProfileMapper extends BaseMapper<UserAbilityProfile> {

    @Update("""
            UPDATE user_ability_profile
               SET status = #{profile.status},
                   evidence_count = #{profile.evidenceCount},
                   last_evaluated_at = #{profile.lastEvaluatedAt},
                   confidence = #{profile.confidence},
                   summary = #{profile.summary},
                   source_type = #{profile.sourceType},
                   deleted = 0,
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_id = #{profile.userId}
               AND skill_code = #{profile.skillCode}
               AND deleted = 1
            """)
    int restoreDeletedEvidenceUsageProfile(
            @Param("profile") UserAbilityProfile profile);
}
