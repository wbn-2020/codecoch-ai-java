package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JobApplicationEventMapper extends BaseMapper<JobApplicationEvent> {

    @Select("""
            SELECT *
              FROM job_application_event
             WHERE user_id = #{userId}
               AND application_id = #{applicationId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             ORDER BY id ASC
             LIMIT 1
            """)
    JobApplicationEvent selectByIdempotencyKey(@Param("userId") Long userId,
                                              @Param("applicationId") Long applicationId,
                                              @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Update("""
            <script>
            UPDATE job_application_event
               SET review_json = #{newReviewJson},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{eventId}
               AND user_id = #{userId}
               AND application_id = #{applicationId}
               AND deleted = 0
               <choose>
                 <when test="oldReviewJson == null">
                   AND review_json IS NULL
                 </when>
                 <otherwise>
                   AND BINARY review_json = BINARY #{oldReviewJson}
                 </otherwise>
               </choose>
            </script>
            """)
    int compareAndSetReviewJson(
            @Param("eventId") Long eventId,
            @Param("userId") Long userId,
            @Param("applicationId") Long applicationId,
            @Param("oldReviewJson") String oldReviewJson,
            @Param("newReviewJson") String newReviewJson);

    @Select("""
            <script>
            SELECT app_event.*
              FROM job_application_event app_event
              JOIN job_application application
                ON application.id = app_event.application_id
               AND application.user_id = #{userId}
               AND application.deleted = 0
               AND COALESCE(application.created_at, application.applied_at, application.updated_at)
                   &lt;= #{sourceCutoffAt}
               AND (application.updated_at IS NULL OR application.updated_at &lt;= #{sourceCutoffAt})
             WHERE app_event.user_id = #{userId}
               AND app_event.deleted = 0
               AND app_event.event_time &gt;= #{rangeStartUtc}
               AND app_event.event_time &lt; #{rangeEndUtc}
               AND COALESCE(app_event.created_at, app_event.event_time, app_event.updated_at)
                   &lt;= #{sourceCutoffAt}
               AND (app_event.updated_at IS NULL OR app_event.updated_at &lt;= #{sourceCutoffAt})
               <if test="targetJobId != null">
                 AND application.target_job_id = #{targetJobId}
               </if>
             ORDER BY app_event.event_time ASC, app_event.id ASC
             LIMIT #{limit}
            </script>
            """)
    List<JobApplicationEvent> selectWeeklyEvidenceEvents(
            @Param("userId") Long userId,
            @Param("rangeStartUtc") LocalDateTime rangeStartUtc,
            @Param("rangeEndUtc") LocalDateTime rangeEndUtc,
            @Param("sourceCutoffAt") LocalDateTime sourceCutoffAt,
            @Param("targetJobId") Long targetJobId,
            @Param("limit") Integer limit);
}
