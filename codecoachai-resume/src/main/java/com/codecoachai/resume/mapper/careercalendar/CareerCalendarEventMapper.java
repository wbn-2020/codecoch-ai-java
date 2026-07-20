package com.codecoachai.resume.mapper.careercalendar;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerCalendarEventMapper extends BaseMapper<CareerCalendarEvent> {

    @Select("""
            SELECT *
            FROM career_calendar_event
            WHERE user_id = #{userId}
              AND BINARY external_uid = BINARY #{externalUid}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    CareerCalendarEvent selectActiveByExternalUidBinaryForUpdate(
            @Param("userId") Long userId,
            @Param("externalUid") String externalUid);

    @Update("""
            <script>
            UPDATE career_calendar_event
               SET preparation_json = #{preparationJson},
                   preparation_status = #{preparationStatus},
                   preparation_ai_call_log_id = #{aiCallLogId},
                   preparation_generated_at = #{generatedAt},
                   preparation_source_hash = #{sourceHash},
                   updated_at = updated_at
             WHERE id = #{eventId}
               AND user_id = #{userId}
               AND deleted = 0
               <choose>
                 <when test="expectedUpdatedAt == null">
                   AND updated_at IS NULL
                 </when>
                 <otherwise>
                   AND updated_at = #{expectedUpdatedAt}
                 </otherwise>
               </choose>
               <choose>
                 <when test="expectedPreparationStatus == null">
                   AND preparation_status IS NULL
                 </when>
                 <otherwise>
                   AND BINARY preparation_status = BINARY #{expectedPreparationStatus}
                 </otherwise>
               </choose>
               <choose>
                 <when test="expectedSourceHash == null">
                   AND preparation_source_hash IS NULL
                 </when>
                 <otherwise>
                   AND BINARY preparation_source_hash = BINARY #{expectedSourceHash}
                 </otherwise>
               </choose>
               <choose>
                 <when test="expectedGeneratedAt == null">
                   AND preparation_generated_at IS NULL
                 </when>
                 <otherwise>
                   AND preparation_generated_at = #{expectedGeneratedAt}
                 </otherwise>
               </choose>
            </script>
            """)
    int compareAndSetPreparation(
            @Param("eventId") Long eventId,
            @Param("userId") Long userId,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
            @Param("expectedPreparationStatus") String expectedPreparationStatus,
            @Param("expectedSourceHash") String expectedSourceHash,
            @Param("expectedGeneratedAt") LocalDateTime expectedGeneratedAt,
            @Param("preparationJson") String preparationJson,
            @Param("preparationStatus") String preparationStatus,
            @Param("aiCallLogId") Long aiCallLogId,
            @Param("generatedAt") LocalDateTime generatedAt,
            @Param("sourceHash") String sourceHash);

    @Update("""
            UPDATE career_calendar_event
               SET preparation_status = 'STALE',
                   updated_at = updated_at
             WHERE id = #{eventId}
               AND user_id = #{userId}
               AND deleted = 0
               AND (
                    preparation_json IS NOT NULL
                    OR preparation_status IS NOT NULL
                    OR preparation_source_hash IS NOT NULL
                    OR preparation_generated_at IS NOT NULL
               )
            """)
    int markPreparationStale(
            @Param("eventId") Long eventId,
            @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT calendar.*
              FROM career_calendar_event calendar
              LEFT JOIN job_application application
                ON application.id = calendar.application_id
               AND application.user_id = #{userId}
               AND application.deleted = 0
               AND COALESCE(application.created_at, application.applied_at, application.updated_at)
                   &lt;= #{sourceCutoffAt}
               AND (application.updated_at IS NULL OR application.updated_at &lt;= #{sourceCutoffAt})
             WHERE calendar.user_id = #{userId}
               AND calendar.deleted = 0
               AND calendar.starts_at_utc &gt;= #{rangeStartUtc}
               AND calendar.starts_at_utc &lt; #{rangeEndUtc}
               AND COALESCE(calendar.created_at, calendar.starts_at_utc, calendar.updated_at)
                   &lt;= #{sourceCutoffAt}
               AND (calendar.updated_at IS NULL OR calendar.updated_at &lt;= #{sourceCutoffAt})
               AND (calendar.application_id IS NULL OR application.id IS NOT NULL)
               <if test="targetJobId != null">
                 AND application.target_job_id = #{targetJobId}
               </if>
             ORDER BY calendar.starts_at_utc ASC, calendar.id ASC
             LIMIT #{limit}
            </script>
            """)
    List<CareerCalendarEvent> selectWeeklyEvidenceEvents(
            @Param("userId") Long userId,
            @Param("rangeStartUtc") LocalDateTime rangeStartUtc,
            @Param("rangeEndUtc") LocalDateTime rangeEndUtc,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("targetJobId") Long targetJobId,
            @Param("limit") Integer limit);
}
