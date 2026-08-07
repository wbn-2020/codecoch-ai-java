package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectSkillEvidenceMapper extends BaseMapper<ProjectSkillEvidence> {

    @Select("""
            <script>
            SELECT project_evidence_id AS projectEvidenceId,
                   COUNT(*) AS confirmedCount
              FROM project_skill_evidence
             WHERE user_id = #{userId}
               AND confirmed = 1
               AND deleted = 0
               AND project_evidence_id IN
               <foreach collection="projectEvidenceIds" item="projectEvidenceId"
                        open="(" separator="," close=")">
                   #{projectEvidenceId}
               </foreach>
             GROUP BY project_evidence_id
            </script>
            """)
    List<ConfirmedCount> selectConfirmedCounts(@Param("userId") Long userId,
                                                @Param("projectEvidenceIds") List<Long> projectEvidenceIds);

    @Data
    class ConfirmedCount {
        private Long projectEvidenceId;
        private Long confirmedCount;
    }
}
