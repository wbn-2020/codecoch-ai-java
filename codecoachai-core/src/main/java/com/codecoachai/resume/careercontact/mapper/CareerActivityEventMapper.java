package com.codecoachai.resume.careercontact.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careercontact.entity.CareerActivityEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerActivityEventMapper extends BaseMapper<CareerActivityEvent> {
    @Select("""
            SELECT * FROM career_activity_event
             WHERE user_id = #{userId}
               AND activity_id = #{activityId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerActivityEvent selectByIdempotency(@Param("userId") Long userId,
                                           @Param("activityId") Long activityId,
                                           @Param("idempotencyKeyHash") String idempotencyKeyHash);
}
