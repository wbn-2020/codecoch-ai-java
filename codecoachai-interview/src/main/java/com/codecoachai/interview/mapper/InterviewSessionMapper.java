package com.codecoachai.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.interview.domain.entity.InterviewSession;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

    @Select("""
            <script>
            SELECT session_item.*
              FROM interview_session session_item
             WHERE session_item.user_id = #{userId}
               AND session_item.deleted = 0
               AND session_item.end_time &gt;= #{rangeStartUtc}
               AND session_item.end_time &lt; #{rangeEndUtc}
               AND session_item.end_time &lt;= #{sourceCutoffAt}
               AND (session_item.created_at IS NULL
                    OR session_item.created_at &lt;= #{sourceCutoffAt})
               AND (session_item.updated_at IS NULL
                    OR session_item.updated_at &lt;= #{sourceCutoffAt})
               <if test="targetJobId != null">
                 AND session_item.target_job_id = #{targetJobId}
               </if>
             ORDER BY session_item.end_time ASC, session_item.id ASC
             LIMIT #{limit}
            </script>
            """)
    List<InterviewSession> selectWeeklyEvidenceSessions(
            @Param("userId") Long userId,
            @Param("rangeStartUtc") LocalDateTime rangeStartUtc,
            @Param("rangeEndUtc") LocalDateTime rangeEndUtc,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("targetJobId") Long targetJobId,
            @Param("limit") Integer limit);

    @Select("""
            <script>
            SELECT session_item.*
              FROM interview_session session_item
             WHERE session_item.user_id = #{userId}
               AND session_item.deleted = 0
               AND (session_item.created_at IS NULL
                    OR session_item.created_at &lt;= #{sourceCutoffAt})
               AND (session_item.updated_at IS NULL
                    OR session_item.updated_at &lt;= #{sourceCutoffAt})
               <if test="targetJobId != null">
                 AND session_item.target_job_id = #{targetJobId}
               </if>
               AND session_item.id IN
               <foreach collection="sessionIds" item="sessionId" open="(" separator="," close=")">
                 #{sessionId}
               </foreach>
             ORDER BY COALESCE(session_item.end_time, session_item.start_time,
                               session_item.created_at) ASC,
                      session_item.id ASC
            </script>
            """)
    List<InterviewSession> selectWeeklyEvidenceSessionsByIds(
            @Param("userId") Long userId,
            @Param("sessionIds") List<Long> sessionIds,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("targetJobId") Long targetJobId);
}
