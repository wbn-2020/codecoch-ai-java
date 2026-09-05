package com.codecoachai.resume.mapper.experimentv2;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.experimentv2.entity.ExperimentAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExperimentAssignmentMapper extends BaseMapper<ExperimentAssignment> {

    @Select("""
            SELECT *
            FROM job_experiment_assignment
            WHERE user_id = #{userId}
              AND hypothesis_id = #{hypothesisId}
              AND application_id = #{applicationId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    ExperimentAssignment selectActiveWinnerForUpdate(
            @Param("userId") Long userId,
            @Param("hypothesisId") Long hypothesisId,
            @Param("applicationId") Long applicationId);
}
