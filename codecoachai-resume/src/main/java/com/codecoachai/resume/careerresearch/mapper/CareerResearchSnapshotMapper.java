package com.codecoachai.resume.careerresearch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerResearchSnapshotMapper extends BaseMapper<CareerResearchSnapshot> {
    @Select("""
            SELECT * FROM career_research_snapshot
             WHERE user_id = #{userId}
               AND report_id = #{reportId}
               AND source_set_hash = #{sourceSetHash}
               AND deleted = 0
             ORDER BY id DESC
             LIMIT 1
            """)
    CareerResearchSnapshot selectWinner(@Param("userId") Long userId,
                                        @Param("reportId") Long reportId,
                                        @Param("sourceSetHash") String sourceSetHash);
}
