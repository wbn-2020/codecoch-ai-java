package com.codecoachai.resume.careerresearch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSourceVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerResearchSourceVersionMapper extends BaseMapper<CareerResearchSourceVersion> {
    @Select("""
            SELECT * FROM career_research_source_version
             WHERE user_id = #{userId} AND source_id = #{sourceId}
               AND content_hash = #{contentHash} AND deleted = 0
             LIMIT 1
            """)
    CareerResearchSourceVersion selectByContentHash(@Param("userId") Long userId,
                                                     @Param("sourceId") Long sourceId,
                                                     @Param("contentHash") String contentHash);
}
