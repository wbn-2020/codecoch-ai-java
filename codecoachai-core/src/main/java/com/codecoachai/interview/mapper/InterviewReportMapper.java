package com.codecoachai.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.interview.domain.entity.InterviewReport;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InterviewReportMapper extends BaseMapper<InterviewReport> {

    @Select("""
            <script>
            SELECT report_item.*
              FROM interview_report report_item
              JOIN interview_session session_item
                ON session_item.id = report_item.session_id
               AND session_item.user_id = #{userId}
               AND session_item.deleted = 0
               AND (session_item.created_at IS NULL
                    OR session_item.created_at &lt;= #{sourceCutoffAt})
               AND (session_item.updated_at IS NULL
                    OR session_item.updated_at &lt;= #{sourceCutoffAt})
             WHERE report_item.user_id = #{userId}
               AND report_item.deleted = 0
               AND (report_item.created_at IS NULL
                    OR report_item.created_at &lt;= #{sourceCutoffAt})
               AND (report_item.updated_at IS NULL
                    OR report_item.updated_at &lt;= #{sourceCutoffAt})
               AND (
                    (session_item.end_time &gt;= #{rangeStartUtc}
                     AND session_item.end_time &lt; #{rangeEndUtc}
                     AND session_item.end_time &lt;= #{sourceCutoffAt})
                    OR
                    (COALESCE(report_item.generated_at, report_item.created_at)
                         &gt;= #{rangeStartUtc}
                     AND COALESCE(report_item.generated_at, report_item.created_at)
                         &lt; #{rangeEndUtc}
                     AND COALESCE(report_item.generated_at, report_item.created_at)
                         &lt;= #{sourceCutoffAt})
               )
               <if test="targetJobId != null">
                 AND session_item.target_job_id = #{targetJobId}
               </if>
             ORDER BY COALESCE(report_item.generated_at, session_item.end_time,
                               report_item.created_at) ASC,
                      report_item.id ASC
             LIMIT #{limit}
            </script>
            """)
    List<InterviewReport> selectWeeklyEvidenceReports(
            @Param("userId") Long userId,
            @Param("rangeStartUtc") LocalDateTime rangeStartUtc,
            @Param("rangeEndUtc") LocalDateTime rangeEndUtc,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("targetJobId") Long targetJobId,
            @Param("limit") Integer limit);
}
