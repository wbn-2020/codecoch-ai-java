package com.codecoachai.ai.agent.mapper.weekly;

import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentWeeklyReportMapper {

    @Insert("""
            INSERT INTO agent_weekly_report (
                user_id, target_job_id, target_scope_key, week_start_date, week_end_date,
                timezone, report_status, snapshot_version, fallback, created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{targetJobId}, #{targetScopeKey}, #{weekStartDate}, #{weekEndDate},
                #{timezone}, 'NOT_GENERATED', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int ensureIdentity(@Param("userId") Long userId,
                       @Param("targetJobId") Long targetJobId,
                       @Param("targetScopeKey") String targetScopeKey,
                       @Param("weekStartDate") LocalDate weekStartDate,
                       @Param("weekEndDate") LocalDate weekEndDate,
                       @Param("timezone") String timezone);

    @Select("""
            SELECT *
            FROM agent_weekly_report
            WHERE user_id = #{userId}
              AND week_start_date = #{weekStartDate}
              AND target_scope_key = #{targetScopeKey}
              AND timezone = #{timezone}
              AND deleted = 0
            LIMIT 1
            """)
    AgentWeeklyReport selectIdentity(@Param("userId") Long userId,
                                     @Param("weekStartDate") LocalDate weekStartDate,
                                     @Param("targetScopeKey") String targetScopeKey,
                                     @Param("timezone") String timezone);

    @Select("""
            SELECT *
            FROM agent_weekly_report
            WHERE id = #{reportId}
              AND user_id = #{userId}
              AND deleted = 0
            LIMIT 1
            """)
    AgentWeeklyReport selectOwned(@Param("userId") Long userId,
                                  @Param("reportId") Long reportId);

    @Select("""
            SELECT *
            FROM agent_weekly_report
            WHERE user_id = #{userId}
              AND week_start_date = #{weekStartDate}
              AND target_scope_key = #{targetScopeKey}
              AND timezone = #{timezone}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    AgentWeeklyReport selectIdentityForUpdate(@Param("userId") Long userId,
                                              @Param("weekStartDate") LocalDate weekStartDate,
                                              @Param("targetScopeKey") String targetScopeKey,
                                              @Param("timezone") String timezone);

    @Update("""
            UPDATE agent_weekly_report
            SET current_snapshot_id = #{snapshotId},
                report_status = #{reportStatus},
                snapshot_version = #{snapshotVersion},
                summary = #{summary},
                confidence_level = #{confidenceLevel},
                fallback = #{fallback},
                fallback_reason = #{fallbackReason},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{reportId}
              AND user_id = #{userId}
              AND deleted = 0
              AND EXISTS (
                  SELECT 1
                  FROM agent_weekly_report_snapshot snapshot
                  WHERE snapshot.id = #{snapshotId}
                    AND snapshot.user_id = #{userId}
                    AND snapshot.weekly_report_id = agent_weekly_report.id
                    AND snapshot.deleted = 0
              )
            """)
    int updateCurrentSnapshot(@Param("userId") Long userId,
                              @Param("reportId") Long reportId,
                              @Param("snapshotId") Long snapshotId,
                              @Param("reportStatus") String reportStatus,
                              @Param("snapshotVersion") Integer snapshotVersion,
                              @Param("summary") String summary,
                              @Param("confidenceLevel") String confidenceLevel,
                              @Param("fallback") Integer fallback,
                              @Param("fallbackReason") String fallbackReason);

    @Update("""
            UPDATE agent_weekly_report
            SET generation_claim_fingerprint = #{generationFingerprint},
                generation_claim_token = #{claimToken},
                generation_claim_idempotency_key_hash = #{idempotencyKeyHash},
                generation_claim_payload_hash = #{idempotencyPayloadHash},
                generation_claimed_at = #{claimedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{reportId}
              AND user_id = #{userId}
              AND deleted = 0
            """)
    int claimGeneration(@Param("userId") Long userId,
                        @Param("reportId") Long reportId,
                        @Param("generationFingerprint") String generationFingerprint,
                        @Param("claimToken") String claimToken,
                        @Param("idempotencyKeyHash") String idempotencyKeyHash,
                        @Param("idempotencyPayloadHash") String idempotencyPayloadHash,
                        @Param("claimedAt") LocalDateTime claimedAt);

    @Update("""
            UPDATE agent_weekly_report
            SET generation_claim_fingerprint = NULL,
                generation_claim_token = NULL,
                generation_claim_idempotency_key_hash = NULL,
                generation_claim_payload_hash = NULL,
                generation_claimed_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{reportId}
              AND user_id = #{userId}
              AND generation_claim_token = #{claimToken}
              AND deleted = 0
            """)
    int clearGenerationClaim(@Param("userId") Long userId,
                             @Param("reportId") Long reportId,
                             @Param("claimToken") String claimToken);

    @Select("""
            <script>
            SELECT report.*
            FROM agent_weekly_report report
            INNER JOIN agent_weekly_report_snapshot current_snapshot
              ON current_snapshot.id = report.current_snapshot_id
             AND current_snapshot.user_id = report.user_id
             AND current_snapshot.user_id = #{userId}
             AND current_snapshot.weekly_report_id = report.id
             AND current_snapshot.deleted = 0
            WHERE report.user_id = #{userId}
              AND report.target_scope_key = #{targetScopeKey}
              AND report.timezone = #{timezone}
              AND report.current_snapshot_id IS NOT NULL
              AND report.deleted = 0
            <if test="weekStartDate != null">
              AND report.week_start_date = #{weekStartDate}
            </if>
            <if test="fromWeekStart != null">
              AND report.week_start_date &gt;= #{fromWeekStart}
            </if>
            <if test="toWeekStart != null">
              AND report.week_start_date &lt;= #{toWeekStart}
            </if>
            ORDER BY report.week_start_date DESC, report.updated_at DESC, report.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AgentWeeklyReport> selectHistoryIdentities(
            @Param("userId") Long userId,
            @Param("targetScopeKey") String targetScopeKey,
            @Param("timezone") String timezone,
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("fromWeekStart") LocalDate fromWeekStart,
            @Param("toWeekStart") LocalDate toWeekStart,
            @Param("limit") Integer limit);
}
