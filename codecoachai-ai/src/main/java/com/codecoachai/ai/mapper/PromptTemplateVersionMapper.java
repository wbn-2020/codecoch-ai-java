package com.codecoachai.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PromptTemplateVersionMapper extends BaseMapper<PromptTemplateVersion> {

    @Select("""
            SELECT v.*
            FROM prompt_template_version v
            INNER JOIN prompt_template t
                ON t.id = v.template_id
               AND t.deleted = 0
            WHERE v.scene = #{scene}
              AND v.status = 'ACTIVE'
              AND v.is_active = 1
              AND v.deleted = 0
              AND t.scene = #{scene}
              AND t.status = 1
              AND (t.enabled = 1 OR t.enabled IS NULL)
            ORDER BY v.activated_at DESC, v.updated_at DESC, v.id DESC
            LIMIT 1
            """)
    PromptTemplateVersion selectActiveVersionOwnedByEnabledTemplate(@Param("scene") String scene);

    @Update("""
            UPDATE prompt_template_version
            SET status = 'INACTIVE',
                is_active = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE scene = #{scene}
              AND id <> #{activeVersionId}
              AND deleted = 0
              AND (status = 'ACTIVE' OR is_active = 1)
            """)
    int deactivateOtherActiveVersionsForScene(
            @Param("scene") String scene,
            @Param("activeVersionId") Long activeVersionId);

    @Update("""
            UPDATE prompt_template_version
            SET status = 'DISABLED',
                change_log = COALESCE(#{changeLog}, change_log),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{versionId}
              AND deleted = 0
              AND is_active = 0
              AND status <> 'ACTIVE'
            """)
    int disableInactiveVersion(
            @Param("versionId") Long versionId,
            @Param("changeLog") String changeLog);
}
