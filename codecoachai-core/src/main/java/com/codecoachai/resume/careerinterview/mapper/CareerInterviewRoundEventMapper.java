package com.codecoachai.resume.careerinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRoundEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerInterviewRoundEventMapper extends BaseMapper<CareerInterviewRoundEvent> {

    @Select("""
            SELECT event.*
              FROM career_interview_round_event event
              JOIN career_interview_round round ON round.id = event.round_id
              JOIN career_interview_process process ON process.id = round.process_id
             WHERE event.round_id = #{roundId}
               AND process.user_id = #{userId}
               AND event.idempotency_key_hash = #{idempotencyKeyHash}
               AND event.deleted = 0
             LIMIT 1
            """)
    CareerInterviewRoundEvent selectByIdempotency(@Param("roundId") Long roundId,
                                                  @Param("userId") Long userId,
                                                  @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT event.*
              FROM career_interview_round_event event
              JOIN career_interview_round round ON round.id = event.round_id
              JOIN career_interview_process process ON process.id = round.process_id
             WHERE process.id = #{processId}
               AND process.user_id = #{userId}
               AND event.event_type = 'ROUND_CREATED'
               AND event.idempotency_key_hash = #{idempotencyKeyHash}
               AND event.deleted = 0
             LIMIT 1
            """)
    CareerInterviewRoundEvent selectCreatedByProcessIdempotency(@Param("processId") Long processId,
                                                                @Param("userId") Long userId,
                                                                @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT event.*
              FROM career_interview_round_event event
              JOIN career_interview_round round ON round.id = event.round_id
              JOIN career_interview_process process ON process.id = round.process_id
             WHERE event.round_id = #{roundId}
               AND process.user_id = #{userId}
               AND event.deleted = 0
             ORDER BY event.occurred_at ASC, event.id ASC
            """)
    List<CareerInterviewRoundEvent> selectByRound(@Param("roundId") Long roundId,
                                                 @Param("userId") Long userId);
}
