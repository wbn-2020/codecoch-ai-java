package com.codecoachai.ai.agent.mapper.weekly;

import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentWeeklyReportSnapshotMapper {

    @Insert("""
            INSERT INTO agent_weekly_report_snapshot (
                user_id, weekly_report_id, snapshot_version, week_start_date, week_end_date,
                target_scope_key, range_start_utc, range_end_utc, source_cutoff_at,
                input_hash, generation_fingerprint, idempotency_key_hash,
                idempotency_payload_hash, request_id, calculation_version,
                prompt_schema_version, output_schema_version, report_status, summary,
                confidence_level, facts_json, signals_json, hypotheses_json,
                experiment_suggestions_json, plan_draft_json, coverage_json,
                result_source, fallback, fallback_reason, trace_id, ai_call_log_id,
                generated_at, created_at, updated_at, deleted
            ) VALUES (
                #{userId}, #{weeklyReportId}, #{snapshotVersion}, #{weekStartDate}, #{weekEndDate},
                #{targetScopeKey}, #{rangeStartUtc}, #{rangeEndUtc}, #{sourceCutoffAt},
                #{inputHash}, #{generationFingerprint}, #{idempotencyKeyHash},
                #{idempotencyPayloadHash}, #{requestId}, #{calculationVersion},
                COALESCE(#{promptSchemaVersion}, 'NONE'),
                #{outputSchemaVersion}, #{reportStatus}, #{summary},
                #{confidenceLevel}, #{factsJson}, #{signalsJson}, #{hypothesesJson},
                #{experimentSuggestionsJson}, #{planDraftJson}, #{coverageJson},
                #{resultSource}, #{fallback}, #{fallbackReason}, #{traceId}, #{aiCallLogId},
                #{generatedAt}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSnapshot(AgentWeeklyReportSnapshot snapshot);

    @Select("""
            SELECT snapshot.*
            FROM agent_weekly_report_snapshot snapshot
            INNER JOIN agent_weekly_report report
              ON report.id = snapshot.weekly_report_id
             AND report.user_id = snapshot.user_id
             AND report.user_id = #{userId}
             AND report.deleted = 0
            WHERE snapshot.user_id = #{userId}
              AND snapshot.idempotency_key_hash = #{idempotencyKeyHash}
              AND snapshot.idempotency_key_hash IS NOT NULL
              AND snapshot.deleted = 0
            LIMIT 1
            """)
    AgentWeeklyReportSnapshot selectByIdempotencyKey(@Param("userId") Long userId,
                                                     @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT snapshot.*
            FROM agent_weekly_report_snapshot snapshot
            INNER JOIN agent_weekly_report report
              ON report.id = snapshot.weekly_report_id
             AND report.user_id = snapshot.user_id
             AND report.user_id = #{userId}
             AND report.deleted = 0
            WHERE snapshot.user_id = #{userId}
              AND snapshot.weekly_report_id = #{weeklyReportId}
              AND report.id = #{weeklyReportId}
              AND snapshot.input_hash = #{inputHash}
              AND snapshot.deleted = 0
            LIMIT 1
            """)
    AgentWeeklyReportSnapshot selectByInputHash(@Param("userId") Long userId,
                                                @Param("weeklyReportId") Long weeklyReportId,
                                                @Param("inputHash") String inputHash);

    @Select("""
            SELECT snapshot.*
            FROM agent_weekly_report_snapshot snapshot
            INNER JOIN agent_weekly_report report
              ON report.id = snapshot.weekly_report_id
             AND report.user_id = snapshot.user_id
             AND report.user_id = #{userId}
             AND report.deleted = 0
            WHERE snapshot.user_id = #{userId}
              AND snapshot.weekly_report_id = #{weeklyReportId}
              AND report.id = #{weeklyReportId}
              AND snapshot.generation_fingerprint = #{generationFingerprint}
              AND snapshot.deleted = 0
            LIMIT 1
            """)
    AgentWeeklyReportSnapshot selectByGenerationFingerprint(
            @Param("userId") Long userId,
            @Param("weeklyReportId") Long weeklyReportId,
            @Param("generationFingerprint") String generationFingerprint);

    @Select("""
            SELECT snapshot.*
            FROM agent_weekly_report_snapshot snapshot
            INNER JOIN agent_weekly_report report
              ON report.id = snapshot.weekly_report_id
             AND report.user_id = snapshot.user_id
             AND report.user_id = #{userId}
             AND report.deleted = 0
            WHERE snapshot.id = #{snapshotId}
              AND snapshot.user_id = #{userId}
              AND snapshot.weekly_report_id = #{weeklyReportId}
              AND report.id = #{weeklyReportId}
              AND snapshot.deleted = 0
            LIMIT 1
            """)
    AgentWeeklyReportSnapshot selectOwnedSnapshot(@Param("userId") Long userId,
                                                  @Param("weeklyReportId") Long weeklyReportId,
                                                  @Param("snapshotId") Long snapshotId);

    @Select("""
            SELECT snapshot.*
            FROM agent_weekly_report_snapshot snapshot
            INNER JOIN agent_weekly_report report
              ON report.id = snapshot.weekly_report_id
             AND report.user_id = snapshot.user_id
             AND report.user_id = #{userId}
             AND report.deleted = 0
            WHERE snapshot.user_id = #{userId}
              AND snapshot.weekly_report_id = #{weeklyReportId}
              AND report.id = #{weeklyReportId}
              AND snapshot.deleted = 0
            ORDER BY snapshot.snapshot_version DESC, snapshot.id DESC
            """)
    List<AgentWeeklyReportSnapshot> selectHistory(@Param("userId") Long userId,
                                                  @Param("weeklyReportId") Long weeklyReportId);

    @Select("""
            SELECT COALESCE(MAX(snapshot.snapshot_version), 0)
            FROM agent_weekly_report_snapshot snapshot
            INNER JOIN agent_weekly_report report
              ON report.id = snapshot.weekly_report_id
             AND report.user_id = snapshot.user_id
             AND report.user_id = #{userId}
             AND report.deleted = 0
            WHERE snapshot.user_id = #{userId}
              AND snapshot.weekly_report_id = #{weeklyReportId}
              AND report.id = #{weeklyReportId}
              AND snapshot.deleted = 0
            """)
    Integer selectMaxVersion(@Param("userId") Long userId,
                             @Param("weeklyReportId") Long weeklyReportId);

    @Select("""
            SELECT s.*
            FROM agent_weekly_report_snapshot s
            INNER JOIN agent_weekly_report r
              ON r.current_snapshot_id = s.id
             AND s.weekly_report_id = r.id
             AND s.user_id = r.user_id
             AND s.user_id = #{userId}
            WHERE r.user_id = #{userId}
              AND r.target_scope_key = #{targetScopeKey}
              AND r.timezone = #{timezone}
              AND r.week_start_date < #{weekStartDate}
              AND r.deleted = 0
              AND s.deleted = 0
              AND s.weekly_report_id = r.id
              AND s.source_cutoff_at <= #{sourceCutoffAt}
              AND s.calculation_version = #{calculationVersion}
              AND s.output_schema_version = #{outputSchemaVersion}
            ORDER BY r.week_start_date DESC
            LIMIT #{limit}
            """)
    List<AgentWeeklyReportSnapshot> selectComparableCurrentSnapshots(
            @Param("userId") Long userId,
            @Param("targetScopeKey") String targetScopeKey,
            @Param("timezone") String timezone,
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("calculationVersion") String calculationVersion,
            @Param("outputSchemaVersion") String outputSchemaVersion,
            @Param("limit") Integer limit);
}
