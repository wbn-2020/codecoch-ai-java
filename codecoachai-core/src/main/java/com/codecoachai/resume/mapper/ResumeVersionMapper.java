package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {

    @Select("""
            SELECT id,
                   user_id AS userId,
                   resume_id AS resumeId,
                   version_no AS versionNo,
                   version_name AS versionName,
                   snapshot_json AS snapshotJson,
                   source_type AS sourceType,
                   source_id AS sourceId,
                   current_flag AS currentFlag,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted
              FROM resume_version
             WHERE user_id = #{userId}
               AND resume_id = #{resumeId}
               AND current_flag = 1
               AND deleted = 0
             LIMIT 1 FOR UPDATE
            """)
    ResumeVersion selectCurrentForUpdate(@Param("userId") Long userId, @Param("resumeId") Long resumeId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   resume_id AS resumeId,
                   version_no AS versionNo,
                   version_name AS versionName,
                   snapshot_json AS snapshotJson,
                   source_type AS sourceType,
                   source_id AS sourceId,
                   current_flag AS currentFlag,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted
              FROM resume_version
             WHERE user_id = #{userId}
               AND resume_id = #{resumeId}
               AND deleted = 0
             ORDER BY version_no DESC, id DESC
             LIMIT 1 FOR UPDATE
            """)
    ResumeVersion selectLatestForUpdate(@Param("userId") Long userId, @Param("resumeId") Long resumeId);
}
