package com.codecoachai.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanDecisionRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentReviewPlanDecisionRequestMapper extends BaseMapper<AgentReviewPlanDecisionRequest> {

    @Insert("""
            INSERT INTO agent_review_plan_decision_request (
                user_id,
                review_id,
                decision_request_key_hash,
                decision_payload_hash,
                request_id,
                created_at,
                updated_at,
                deleted
            ) VALUES (
                #{userId},
                #{reviewId},
                #{requestKeyHash},
                #{payloadHash},
                #{requestId},
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                0
            )
            """)
    int insertIdempotencyRequest(@Param("userId") Long userId,
                                 @Param("reviewId") Long reviewId,
                                 @Param("requestKeyHash") String requestKeyHash,
                                 @Param("payloadHash") String payloadHash,
                                 @Param("requestId") String requestId);

    @Select("""
            SELECT *
            FROM agent_review_plan_decision_request
            WHERE user_id = #{userId}
              AND decision_request_key_hash = #{requestKeyHash}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    AgentReviewPlanDecisionRequest selectByUserAndKeyForUpdate(
            @Param("userId") Long userId,
            @Param("requestKeyHash") String requestKeyHash);
}
