package com.codecoachai.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    @Select("""
            SELECT id
            FROM prompt_template
            WHERE scene = #{scene}
              AND deleted = 0
            ORDER BY id
            FOR UPDATE
            """)
    List<Long> lockSceneTemplatesForActivation(@Param("scene") String scene);
}
