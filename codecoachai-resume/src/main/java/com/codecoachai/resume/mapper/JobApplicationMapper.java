package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.dto.ApplicationStatsAggregate;
import com.codecoachai.resume.domain.dto.ApplicationStatusCount;
import com.codecoachai.resume.domain.entity.JobApplication;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplication> {

    @Select("""
            SELECT COUNT(1) AS total,
                   SUM(CASE WHEN UPPER(TRIM(status)) IN ('SAVED','PREPARING','APPLIED','INTERVIEWING','OFFER')
                            THEN 1 ELSE 0 END) AS activeCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) IN ('SAVED','PREPARING','APPLIED','INTERVIEWING','OFFER')
                                 AND next_follow_up_at IS NOT NULL AND next_follow_up_at < #{now}
                            THEN 1 ELSE 0 END) AS overdueFollowUpCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) IN ('SAVED','PREPARING','APPLIED','INTERVIEWING','OFFER')
                                 AND next_follow_up_at >= #{dayStart} AND next_follow_up_at < #{dayEnd}
                            THEN 1 ELSE 0 END) AS dueTodayFollowUpCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) IN ('SAVED','PREPARING','APPLIED','INTERVIEWING','OFFER')
                                 AND next_follow_up_at IS NULL
                            THEN 1 ELSE 0 END) AS noFollowUpCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) IN ('SAVED','PREPARING','APPLIED','INTERVIEWING','OFFER')
                                 AND updated_at < #{staleBefore}
                            THEN 1 ELSE 0 END) AS staleActiveCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) = 'INTERVIEWING' THEN 1 ELSE 0 END) AS interviewCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) = 'OFFER' THEN 1 ELSE 0 END) AS offerCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) = 'REJECTED' THEN 1 ELSE 0 END) AS rejectedCount,
                   SUM(CASE WHEN UPPER(TRIM(status)) = 'CLOSED' THEN 1 ELSE 0 END) AS closedCount
              FROM job_application
             WHERE user_id = #{userId} AND deleted = 0
            """)
    ApplicationStatsAggregate selectStats(@Param("userId") Long userId,
                                          @Param("now") LocalDateTime now,
                                          @Param("dayStart") LocalDateTime dayStart,
                                          @Param("dayEnd") LocalDateTime dayEnd,
                                          @Param("staleBefore") LocalDateTime staleBefore);

    @Select("""
            SELECT UPPER(TRIM(status)) AS status, COUNT(1) AS count
              FROM job_application
             WHERE user_id = #{userId} AND deleted = 0
             GROUP BY UPPER(TRIM(status))
             ORDER BY UPPER(TRIM(status))
            """)
    List<ApplicationStatusCount> selectStatusCounts(@Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM job_application
             WHERE user_id = #{userId}
               AND deleted = 0
               AND UPPER(TRIM(status)) IN ('SAVED','PREPARING','APPLIED','INTERVIEWING','OFFER')
               AND next_follow_up_at IS NOT NULL
               AND (
                    next_follow_up_at < #{now}
                    OR (next_follow_up_at >= #{dayStart} AND next_follow_up_at < #{dayEnd})
               )
             ORDER BY CASE WHEN next_follow_up_at < #{now} THEN 0 ELSE 1 END,
                      next_follow_up_at ASC,
                      updated_at DESC,
                      id DESC
             LIMIT #{limit}
            """)
    List<JobApplication> selectReminderCandidates(@Param("userId") Long userId,
                                                  @Param("now") LocalDateTime now,
                                                  @Param("dayStart") LocalDateTime dayStart,
                                                  @Param("dayEnd") LocalDateTime dayEnd,
                                                  @Param("limit") Integer limit);

    @Select("""
            SELECT *
              FROM job_application
             WHERE user_id = #{userId}
               AND deleted = 0
               AND COALESCE(applied_at, created_at, updated_at) >= #{startAt}
               AND COALESCE(applied_at, created_at, updated_at) <= #{endAt}
             ORDER BY applied_at DESC, created_at DESC, updated_at DESC, id DESC
            """)
    List<JobApplication> selectInsightRange(@Param("userId") Long userId,
                                            @Param("startAt") LocalDateTime startAt,
                                            @Param("endAt") LocalDateTime endAt);

    @Select("""
            SELECT *
              FROM job_application
             WHERE user_id = #{userId}
               AND deleted = 0
               AND (applied_at BETWEEN #{startAt} AND #{endAt} OR applied_at IS NULL)
             ORDER BY updated_at DESC, id DESC
             LIMIT 1001
            """)
    List<JobApplication> selectCareerImportCandidatesInDateWindow(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    @Select("""
            SELECT *
              FROM job_application
             WHERE user_id = #{userId}
               AND deleted = 0
             ORDER BY updated_at DESC, id DESC
             LIMIT 1001
            """)
    List<JobApplication> selectCareerImportCandidatesForUndated(
            @Param("userId") Long userId);
}
