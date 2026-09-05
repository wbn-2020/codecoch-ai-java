package com.codecoachai.resume.careeroffer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOfferVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerOfferVersionMapper extends BaseMapper<CareerOfferVersion> {

    @Select("""
            SELECT *
              FROM career_offer_version
             WHERE offer_id = #{offerId} AND user_id = #{userId} AND deleted = 0
             ORDER BY version_no DESC, id DESC
            """)
    List<CareerOfferVersion> selectByOffer(@Param("offerId") Long offerId, @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM career_offer_version
             WHERE offer_id = #{offerId}
               AND user_id = #{userId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerOfferVersion selectByIdempotency(@Param("offerId") Long offerId, @Param("userId") Long userId,
                                          @Param("idempotencyKeyHash") String idempotencyKeyHash);
}
