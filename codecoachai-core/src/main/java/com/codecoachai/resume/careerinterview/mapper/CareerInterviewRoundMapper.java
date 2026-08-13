package com.codecoachai.resume.careerinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRound;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerInterviewRoundMapper extends BaseMapper<CareerInterviewRound> {

    @Select("""
            SELECT round.*
              FROM career_interview_round round
              JOIN career_interview_process process ON process.id = round.process_id
             WHERE round.id = #{roundId}
               AND process.user_id = #{userId}
               AND process.deleted = 0
               AND round.deleted = 0
            """)
    CareerInterviewRound selectOwned(@Param("roundId") Long roundId, @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM career_interview_round
             WHERE process_id = #{processId}
               AND deleted = 0
             ORDER BY round_no ASC, id ASC
            """)
    List<CareerInterviewRound> selectByProcess(@Param("processId") Long processId);

    @Select("""
            SELECT round.*
              FROM career_interview_round round
              JOIN career_interview_process process ON process.id = round.process_id
             WHERE round.calendar_event_id = #{calendarEventId}
               AND process.user_id = #{userId}
               AND process.deleted = 0
               AND round.deleted = 0
               AND round.status NOT IN ('CANCELLED','RESCHEDULED')
             LIMIT 1
            """)
    CareerInterviewRound selectActiveByCalendarEvent(@Param("calendarEventId") Long calendarEventId,
                                                    @Param("userId") Long userId);

    @Update("""
            UPDATE career_interview_round
               SET title = #{title},
                   result_summary = #{resultSummary},
                   next_step = #{nextStep},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{roundId}
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int updateDetails(@Param("roundId") Long roundId,
                      @Param("title") String title,
                      @Param("resultSummary") String resultSummary,
                      @Param("nextStep") String nextStep,
                      @Param("expectedLockVersion") Integer expectedLockVersion);

    @Update("""
            UPDATE career_interview_round
               SET status = #{nextStatus},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{roundId}
               AND status = #{expectedStatus}
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int transition(@Param("roundId") Long roundId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus,
                   @Param("expectedLockVersion") Integer expectedLockVersion);

    @Update("""
            UPDATE career_interview_round
               SET status = 'RESCHEDULED',
                   timezone = #{timezone},
                   scheduled_starts_at_utc = #{startsAtUtc},
                   scheduled_ends_at_utc = #{endsAtUtc},
                   calendar_event_id = #{calendarEventId},
                   preparation_source_hash = #{preparationSourceHash},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{roundId}
               AND status = 'SCHEDULED'
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int reschedule(@Param("roundId") Long roundId,
                   @Param("timezone") String timezone,
                   @Param("startsAtUtc") LocalDateTime startsAtUtc,
                   @Param("endsAtUtc") LocalDateTime endsAtUtc,
                   @Param("calendarEventId") Long calendarEventId,
                   @Param("preparationSourceHash") String preparationSourceHash,
                   @Param("expectedLockVersion") Integer expectedLockVersion);

    @Update("""
            UPDATE career_interview_round
               SET calendar_event_id = #{calendarEventId},
                   preparation_source_hash = #{preparationSourceHash},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{roundId}
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int linkCalendar(@Param("roundId") Long roundId,
                     @Param("calendarEventId") Long calendarEventId,
                     @Param("preparationSourceHash") String preparationSourceHash,
                     @Param("expectedLockVersion") Integer expectedLockVersion);
}
