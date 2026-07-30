package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.AbilitySkillNode;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AbilitySkillNodeMapper extends BaseMapper<AbilitySkillNode> {

    @Select("""
            SELECT *
              FROM ability_skill_node
             WHERE enabled = 1
               AND deleted = 0
             ORDER BY sort_order, id
            """)
    List<AbilitySkillNode> selectEnabledForEvidenceMapping();
}
