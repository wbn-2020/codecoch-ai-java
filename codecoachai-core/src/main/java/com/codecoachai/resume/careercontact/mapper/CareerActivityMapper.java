package com.codecoachai.resume.careercontact.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careercontact.entity.CareerActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CareerActivityMapper extends BaseMapper<CareerActivity> {
    @Select("""
            SELECT * FROM career_activity
             WHERE user_id = #{userId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerActivity selectByIdempotency(@Param("userId") Long userId,
                                      @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT * FROM career_activity
             WHERE user_id = #{userId}
               AND deleted = 0
               AND status = 'RECORDED'
               AND next_follow_up_at IS NOT NULL
               AND next_follow_up_at <= #{until}
             ORDER BY next_follow_up_at ASC, id ASC
             LIMIT #{limit}
            """)
    List<CareerActivity> selectDueFollowUps(@Param("userId") Long userId,
                                            @Param("until") java.time.LocalDateTime until,
                                            @Param("limit") int limit);
}
