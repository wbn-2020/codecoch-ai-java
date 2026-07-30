package com.codecoachai.resume.mapper.experimentv2;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.experimentv2.entity.ExperimentAttribution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExperimentAttributionMapper extends BaseMapper<ExperimentAttribution> {

    @Select("""
            SELECT *
             FROM job_experiment_attribution
             WHERE user_id = #{userId}
               AND cohort_id = #{cohortId}
               AND BINARY input_hash = BINARY #{inputHash}
               AND BINARY algorithm_version = BINARY #{algorithmVersion}
               AND deleted = 0
             ORDER BY id DESC
             LIMIT 1
            """)
    ExperimentAttribution selectByIdentity(@Param("userId") Long userId,
                                           @Param("cohortId") Long cohortId,
                                           @Param("inputHash") String inputHash,
                                           @Param("algorithmVersion") String algorithmVersion);
}
