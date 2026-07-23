package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.ProjectEvidenceVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectEvidenceVersionMapper extends BaseMapper<ProjectEvidenceVersion> {

    @Select("""
            SELECT *
              FROM project_evidence_version
             WHERE project_evidence_id = #{projectEvidenceId}
               AND user_id = #{userId}
               AND version_no = #{versionNo}
               AND deleted = 0
             LIMIT 1
            """)
    ProjectEvidenceVersion selectOwnedVersion(@Param("projectEvidenceId") Long projectEvidenceId,
                                              @Param("userId") Long userId,
                                              @Param("versionNo") Integer versionNo);

    @Select("""
            SELECT *
              FROM project_evidence_version
             WHERE project_evidence_id = #{projectEvidenceId}
               AND user_id = #{userId}
               AND content_hash = #{contentHash}
               AND deleted = 0
             LIMIT 1
            """)
    ProjectEvidenceVersion selectByContentHash(@Param("projectEvidenceId") Long projectEvidenceId,
                                               @Param("userId") Long userId,
                                               @Param("contentHash") String contentHash);

    @Select("""
            SELECT *
              FROM project_evidence_version
             WHERE project_evidence_id = #{projectEvidenceId}
               AND user_id = #{userId}
               AND deleted = 0
             ORDER BY version_no DESC, id DESC
             LIMIT 1 FOR UPDATE
            """)
    ProjectEvidenceVersion selectLatestForUpdate(@Param("projectEvidenceId") Long projectEvidenceId,
                                                 @Param("userId") Long userId);
}
