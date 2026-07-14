package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.ResumeProject;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeProjectMapper extends BaseMapper<ResumeProject> {

    @Select("""
            SELECT id,
                   resume_id AS resumeId,
                   project_name AS projectName,
                   project_period AS projectPeriod,
                   project_background AS projectBackground,
                   role,
                   tech_stack AS techStack,
                   responsibility,
                   core_features AS coreFeatures,
                   technical_difficulties AS technicalDifficulties,
                   optimization_results AS optimizationResults,
                   description,
                   highlights,
                   sort,
                   sort_order AS sortOrder,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted
              FROM resume_project
             WHERE resume_id = #{resumeId}
               AND deleted = 0
             ORDER BY sort_order, sort, id
             FOR UPDATE
            """)
    List<ResumeProject> selectActiveByResumeIdForUpdate(@Param("resumeId") Long resumeId);
}
