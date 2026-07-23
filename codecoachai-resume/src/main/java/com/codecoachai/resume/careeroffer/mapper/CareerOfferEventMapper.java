package com.codecoachai.resume.careeroffer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOfferEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerOfferEventMapper extends BaseMapper<CareerOfferEvent> {

    @Select("""
            SELECT *
              FROM career_offer_event
             WHERE offer_id = #{offerId}
               AND user_id = #{userId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerOfferEvent selectByIdempotency(@Param("offerId") Long offerId, @Param("userId") Long userId,
                                        @Param("idempotencyKeyHash") String idempotencyKeyHash);
}
