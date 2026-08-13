package com.codecoachai.resume.careeroffer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOffer;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerOfferMapper extends BaseMapper<CareerOffer> {

    @Select("""
            SELECT *
              FROM career_offer
             WHERE id = #{offerId} AND user_id = #{userId} AND deleted = 0
            """)
    CareerOffer selectOwned(@Param("offerId") Long offerId, @Param("userId") Long userId);

    @Select("""
            SELECT offer.*
              FROM career_offer offer
              JOIN job_application application ON application.id = offer.application_id
             WHERE application.id = #{applicationId}
               AND application.user_id = #{userId}
               AND application.deleted = 0
               AND offer.user_id = #{userId}
               AND offer.deleted = 0
            """)
    CareerOffer selectByApplication(@Param("applicationId") Long applicationId,
                                    @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM career_offer
             WHERE user_id = #{userId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerOffer selectByIdempotency(@Param("userId") Long userId,
                                   @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT offer.*
              FROM career_offer offer
              JOIN job_application application ON application.id = offer.application_id
             WHERE application.campaign_id = #{campaignId}
               AND application.user_id = #{userId}
               AND application.deleted = 0
               AND offer.user_id = #{userId}
               AND offer.deleted = 0
             ORDER BY offer.id ASC
            """)
    List<CareerOffer> selectByCampaign(@Param("campaignId") Long campaignId,
                                       @Param("userId") Long userId);

    @Update("""
            UPDATE career_offer
               SET next_version_no = next_version_no + 1,
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{offerId}
               AND user_id = #{userId}
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int claimNextVersion(@Param("offerId") Long offerId, @Param("userId") Long userId,
                         @Param("expectedLockVersion") Integer expectedLockVersion);

    @Update("""
            UPDATE career_offer
               SET current_version_id = #{versionId},
                   decision_deadline = #{decisionDeadline},
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{offerId}
               AND user_id = #{userId}
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int attachVersion(@Param("offerId") Long offerId, @Param("userId") Long userId,
                      @Param("versionId") Long versionId,
                      @Param("decisionDeadline") LocalDateTime decisionDeadline,
                      @Param("expectedLockVersion") Integer expectedLockVersion);

    @Update("""
            UPDATE career_offer
               SET status = #{nextStatus},
                   finalized_at = CASE WHEN #{nextStatus} IN ('ACCEPTED','DECLINED','EXPIRED','WITHDRAWN')
                                       THEN CURRENT_TIMESTAMP ELSE finalized_at END,
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{offerId}
               AND user_id = #{userId}
               AND deleted = 0
               AND status = #{expectedStatus}
               AND lock_version = #{expectedLockVersion}
            """)
    int transition(@Param("offerId") Long offerId, @Param("userId") Long userId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus,
                   @Param("expectedLockVersion") Integer expectedLockVersion);

    @Select("""
            SELECT offer.*
              FROM career_offer offer
             WHERE offer.user_id = #{userId}
               AND offer.deleted = 0
               AND offer.status IN ('RECEIVED','NEGOTIATING')
               AND offer.decision_deadline IS NOT NULL
               AND offer.decision_deadline >= #{fromAt}
               AND offer.decision_deadline < #{toAt}
             ORDER BY offer.decision_deadline ASC, offer.id ASC
             LIMIT #{limit}
            """)
    List<CareerOffer> selectReminderCandidates(@Param("userId") Long userId,
                                               @Param("fromAt") LocalDateTime fromAt,
                                               @Param("toAt") LocalDateTime toAt,
                                               @Param("limit") int limit);
}
