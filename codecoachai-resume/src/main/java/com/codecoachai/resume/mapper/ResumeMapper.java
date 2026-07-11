package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeMapper extends BaseMapper<Resume> {

    @Select("""
            SELECT id
              FROM resume
             WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
             FOR UPDATE
            """)
    Long lockOwnedResume(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT r.id,
                   r.title,
                   r.real_name AS realName,
                   r.target_position AS targetPosition,
                   r.skill_stack AS skillStack,
                   r.summary,
                   COALESCE(pc.project_count, 0) AS projectCount,
                   r.is_default AS isDefault,
                   r.status,
                   r.updated_at AS updatedAt
              FROM resume r
              LEFT JOIN (
                    SELECT resume_id, COUNT(1) AS project_count
                      FROM resume_project
                     WHERE deleted = 0
                     GROUP BY resume_id
              ) pc ON pc.resume_id = r.id
             WHERE r.user_id = #{userId}
               AND r.deleted = 0
            <if test="keywordPattern != null and keywordPattern != ''">
               AND (
                    r.title LIKE #{keywordPattern} ESCAPE '!'
                    OR r.real_name LIKE #{keywordPattern} ESCAPE '!'
                    OR r.target_position LIKE #{keywordPattern} ESCAPE '!'
                    OR r.skill_stack LIKE #{keywordPattern} ESCAPE '!'
                    OR r.summary LIKE #{keywordPattern} ESCAPE '!'
               )
            </if>
             ORDER BY r.is_default DESC, r.updated_at DESC, r.id DESC
            <if test="limit != null">
             LIMIT #{limit} OFFSET #{offset}
            </if>
            </script>
            """)
    List<ResumeListVO> selectResumeList(@Param("userId") Long userId,
                                        @Param("keywordPattern") String keywordPattern,
                                        @Param("offset") Long offset,
                                        @Param("limit") Integer limit);

    @Select("""
            SELECT id, user_id AS userId
              FROM resume
             WHERE deleted = 0 AND id > #{afterId}
             ORDER BY id
             LIMIT #{limit}
            """)
    List<Resume> selectActiveAfter(@Param("afterId") Long afterId, @Param("limit") Integer limit);
}
