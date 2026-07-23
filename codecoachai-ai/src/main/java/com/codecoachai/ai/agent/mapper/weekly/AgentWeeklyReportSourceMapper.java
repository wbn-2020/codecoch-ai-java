package com.codecoachai.ai.agent.mapper.weekly;

import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentWeeklyReportSourceMapper {

    @Insert("""
            <script>
            INSERT INTO agent_weekly_report_source (
                user_id, snapshot_id, source_type, source_id, source_time, source_updated_at,
                scope_key, inclusion_status, exclude_reason, source_hash, safe_summary,
                metadata_json, created_at, updated_at, deleted
            ) VALUES
            <foreach collection="items" item="item" separator=",">
                (
                    #{item.userId}, #{item.snapshotId}, #{item.sourceType}, #{item.sourceId},
                    #{item.sourceTime}, #{item.sourceUpdatedAt}, #{item.scopeKey},
                    #{item.inclusionStatus}, #{item.excludeReason}, #{item.sourceHash},
                    #{item.safeSummary}, #{item.metadataJson},
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<AgentWeeklyReportSource> items);

    @Select("""
            SELECT source.*
            FROM agent_weekly_report_source source
            INNER JOIN agent_weekly_report_snapshot snapshot
              ON snapshot.id = source.snapshot_id
             AND snapshot.user_id = source.user_id
             AND snapshot.user_id = #{userId}
             AND snapshot.deleted = 0
            INNER JOIN agent_weekly_report report
              ON report.id = snapshot.weekly_report_id
             AND report.user_id = snapshot.user_id
             AND report.user_id = #{userId}
             AND report.deleted = 0
            WHERE source.user_id = #{userId}
              AND source.snapshot_id = #{snapshotId}
              AND snapshot.id = #{snapshotId}
              AND source.deleted = 0
            ORDER BY source.id ASC
            """)
    List<AgentWeeklyReportSource> selectBySnapshot(@Param("userId") Long userId,
                                                   @Param("snapshotId") Long snapshotId);
}
