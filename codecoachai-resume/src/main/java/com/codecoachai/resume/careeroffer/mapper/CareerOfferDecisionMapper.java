package com.codecoachai.resume.careeroffer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerOfferDecisionMapper extends BaseMapper<CareerOfferDecision> {

    @Select("""
            SELECT *
              FROM career_offer_decision
             WHERE id = #{decisionId} AND user_id = #{userId} AND deleted = 0
            """)
    CareerOfferDecision selectOwned(@Param("decisionId") Long decisionId, @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM career_offer_decision
             WHERE campaign_id = #{campaignId}
               AND user_id = #{userId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerOfferDecision selectByIdempotency(@Param("campaignId") Long campaignId, @Param("userId") Long userId,
                                           @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Update("""
            UPDATE career_offer_decision
               SET status = #{status},
                   selected_offer_id = #{selectedOfferId},
                   outcome = #{outcome},
                   confirmed_at = CURRENT_TIMESTAMP,
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{decisionId}
               AND user_id = #{userId}
               AND status = 'PREVIEWED'
               AND lock_version = #{expectedLockVersion}
               AND deleted = 0
            """)
    int confirm(@Param("decisionId") Long decisionId, @Param("userId") Long userId,
                @Param("selectedOfferId") Long selectedOfferId, @Param("outcome") String outcome,
                @Param("expectedLockVersion") Integer expectedLockVersion);
}
