package com.codecoachai.resume.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AbilityTrainingEvidenceMapper {

    @Select("""
            SELECT NULLIF(TRIM(related_skill_code), '') AS skillCode,
                   NULLIF(TRIM(related_skill_name), '') AS skillName,
                   COUNT(*) AS evidenceCount,
                   MAX(COALESCE(completed_at, updated_at)) AS lastCompletedAt
              FROM agent_task
             WHERE user_id = #{userId}
               AND status = 'DONE'
               AND deleted = 0
               AND (
                    NULLIF(TRIM(related_skill_code), '') IS NOT NULL
                    OR NULLIF(TRIM(related_skill_name), '') IS NOT NULL
               )
             GROUP BY NULLIF(TRIM(related_skill_code), ''),
                      NULLIF(TRIM(related_skill_name), '')
            """)
    List<TrainingEvidenceAggregate> selectCompletedSkillAggregates(
            @Param("userId") Long userId);

    @Data
    class TrainingEvidenceAggregate {
        private String skillCode;
        private String skillName;
        private Long evidenceCount;
        private LocalDateTime lastCompletedAt;
    }
}
