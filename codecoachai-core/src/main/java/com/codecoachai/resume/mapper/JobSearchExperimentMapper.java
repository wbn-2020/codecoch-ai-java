package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface JobSearchExperimentMapper extends BaseMapper<JobSearchExperiment> {

    @Select("""
            <script>
            SELECT experiment.*
              FROM job_search_experiment experiment
             WHERE experiment.user_id = #{userId}
               AND experiment.deleted = 0
               AND experiment.demo_flag = 0
               AND COALESCE(experiment.created_at, experiment.updated_at) &lt;= #{sourceCutoffAt}
               AND (experiment.updated_at IS NULL OR experiment.updated_at &lt;= #{sourceCutoffAt})
               AND (experiment.start_date IS NULL OR experiment.start_date &lt; #{rangeEndDateExclusive})
               AND (experiment.end_date IS NULL OR experiment.end_date &gt;= #{rangeStartDate})
               <if test="experimentIds != null and experimentIds.size() &gt; 0">
                 AND experiment.id IN
                 <foreach collection="experimentIds" item="experimentId" open="(" separator="," close=")">
                   #{experimentId}
                 </foreach>
               </if>
               <if test="targetJobId != null">
                 AND EXISTS (
                     SELECT 1
                       FROM job_search_experiment_relation relation_item
                       LEFT JOIN job_application application
                         ON relation_item.relation_type = 'JOB_APPLICATION'
                        AND application.id = relation_item.relation_id
                        AND application.user_id = #{userId}
                        AND application.deleted = 0
                        AND COALESCE(application.created_at, application.applied_at, application.updated_at)
                            &lt;= #{sourceCutoffAt}
                        AND (application.updated_at IS NULL
                            OR application.updated_at &lt;= #{sourceCutoffAt})
                       LEFT JOIN job_description_analysis analysis
                         ON relation_item.relation_type = 'JD_ANALYSIS'
                        AND analysis.id = relation_item.relation_id
                        AND analysis.user_id = #{userId}
                        AND analysis.deleted = 0
                        AND COALESCE(analysis.created_at, analysis.updated_at) &lt;= #{sourceCutoffAt}
                        AND (analysis.updated_at IS NULL OR analysis.updated_at &lt;= #{sourceCutoffAt})
                      WHERE relation_item.experiment_id = experiment.id
                        AND relation_item.user_id = #{userId}
                        AND relation_item.deleted = 0
                        AND relation_item.demo_flag = 0
                        AND COALESCE(relation_item.created_at, relation_item.updated_at) &lt;= #{sourceCutoffAt}
                        AND (relation_item.updated_at IS NULL
                            OR relation_item.updated_at &lt;= #{sourceCutoffAt})
                        AND (
                            (relation_item.relation_type = 'TARGET_JOB'
                                AND relation_item.relation_id = #{targetJobId})
                            OR (relation_item.relation_type = 'JOB_APPLICATION'
                                AND application.target_job_id = #{targetJobId})
                            OR (relation_item.relation_type = 'JD_ANALYSIS'
                                AND analysis.target_job_id = #{targetJobId})
                        )
                 )
               </if>
             ORDER BY experiment.start_date ASC, experiment.id ASC
             LIMIT #{limit}
            </script>
            """)
    List<JobSearchExperiment> selectWeeklyEvidenceExperiments(
            @Param("userId") Long userId,
            @Param("rangeStartDate") LocalDate rangeStartDate,
            @Param("rangeEndDateExclusive") LocalDate rangeEndDateExclusive,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("targetJobId") Long targetJobId,
            @Param("experimentIds") List<Long> experimentIds,
            @Param("limit") Integer limit);
}
