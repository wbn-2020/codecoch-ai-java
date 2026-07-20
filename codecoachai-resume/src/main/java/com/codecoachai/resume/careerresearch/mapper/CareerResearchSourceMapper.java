package com.codecoachai.resume.careerresearch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerResearchSourceMapper extends BaseMapper<CareerResearchSource> {
    @Update("""
            UPDATE career_research_source
               SET current_version_id = #{versionId},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{sourceId} AND user_id = #{userId}
               AND lock_version = #{expectedLockVersion}
               AND deleted = 0 AND status = 'ACTIVE'
            """)
    int advanceCurrentVersion(@Param("sourceId") Long sourceId,
                              @Param("userId") Long userId,
                              @Param("expectedLockVersion") Integer expectedLockVersion,
                              @Param("versionId") Long versionId);
}
