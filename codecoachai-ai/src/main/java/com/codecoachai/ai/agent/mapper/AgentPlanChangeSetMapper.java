package com.codecoachai.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeSet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentPlanChangeSetMapper extends BaseMapper<AgentPlanChangeSet> {

    @Select("""
            SELECT *
            FROM agent_plan_change_set
            WHERE id = #{changeSetId}
              AND user_id = #{userId}
              AND deleted = 0
            FOR UPDATE
            """)
    AgentPlanChangeSet selectOwnedForUpdate(@Param("userId") Long userId,
                                            @Param("changeSetId") Long changeSetId);

    @Select("""
            SELECT *
            FROM agent_plan_change_set
            WHERE user_id = #{userId}
              AND preview_request_key_hash = #{requestKeyHash}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    AgentPlanChangeSet selectByPreviewRequestKey(@Param("userId") Long userId,
                                                 @Param("requestKeyHash") String requestKeyHash);
}
