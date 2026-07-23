package com.codecoachai.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentPlanChangeItemMapper extends BaseMapper<AgentPlanChangeItem> {

    @Select("""
            SELECT *
            FROM agent_plan_change_item
            WHERE user_id = #{userId}
              AND change_set_id = #{changeSetId}
              AND deleted = 0
            ORDER BY id
            FOR UPDATE
            """)
    List<AgentPlanChangeItem> selectByChangeSetForUpdate(@Param("userId") Long userId,
                                                         @Param("changeSetId") Long changeSetId);
}
