package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.JobSearchExperimentRelation;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface JobSearchExperimentRelationMapper extends BaseMapper<JobSearchExperimentRelation> {

    @Select("""
            <script>
            SELECT *
              FROM job_search_experiment_relation
             WHERE user_id = #{userId}
               AND deleted = 0
               AND demo_flag = 0
               AND COALESCE(created_at, updated_at) &lt;= #{sourceCutoffAt}
               AND (updated_at IS NULL OR updated_at &lt;= #{sourceCutoffAt})
               AND experiment_id IN
               <foreach collection="experimentIds" item="experimentId" open="(" separator="," close=")">
                 #{experimentId}
               </foreach>
             ORDER BY experiment_id ASC, created_at ASC, id ASC
             LIMIT #{limit}
            </script>
            """)
    List<JobSearchExperimentRelation> selectWeeklyEvidenceRelations(
            @Param("userId") Long userId,
            @Param("experimentIds") List<Long> experimentIds,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("limit") Integer limit);
}
