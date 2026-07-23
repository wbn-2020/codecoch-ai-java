package com.codecoachai.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentReviewMapper extends BaseMapper<AgentReview> {

    /**
     * Claims the daily review row before the AI call. The row is kept at version 0
     * until the surrounding transaction completes the generated review.
     */
    @Insert("""
            INSERT INTO agent_review (
                user_id, target_job_id, review_date, review_type, source_task_id,
                idempotency_key, target_scope_key, review_version, source_snapshot_hash,
                summary, done_count, skipped_count, todo_count, completion_rate,
                readiness_score, next_actions_json, review_json, confidence_level,
                fallback, deleted
            ) VALUES (
                #{userId}, #{targetJobId, jdbcType=BIGINT}, #{reviewDate}, 'DAILY', NULL,
                #{idempotencyKey}, #{targetScopeKey}, 0, #{sourceSnapshotHash},
                NULL, 0, 0, 0, 0, 0, '[]', '{}', 'LOW', 1, 0
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertDailyGenerationClaim(@Param("userId") Long userId,
                                   @Param("targetJobId") Long targetJobId,
                                   @Param("reviewDate") LocalDate reviewDate,
                                   @Param("idempotencyKey") String idempotencyKey,
                                   @Param("targetScopeKey") String targetScopeKey,
                                   @Param("sourceSnapshotHash") String sourceSnapshotHash);

    @Select("""
            SELECT *
            FROM agent_review
            WHERE id = #{reviewId}
              AND user_id = #{userId}
              AND deleted = 0
            FOR UPDATE
            """)
    AgentReview selectOwnedForUpdate(@Param("userId") Long userId, @Param("reviewId") Long reviewId);

    @Select("""
            SELECT *
            FROM agent_review
            WHERE user_id = #{userId}
              AND review_date = #{reviewDate}
              AND review_type = 'DAILY'
              AND target_scope_key = #{targetScopeKey}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            FOR UPDATE
            """)
    AgentReview selectDailyForUpdate(@Param("userId") Long userId,
                                     @Param("reviewDate") LocalDate reviewDate,
                                     @Param("targetScopeKey") String targetScopeKey);
}
