package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerEvidenceUsageMapper extends BaseMapper<CareerEvidenceUsage> {

    @Select("""
            SELECT *
              FROM career_evidence_usage
             WHERE user_id = #{userId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerEvidenceUsage selectByIdempotencyKey(@Param("userId") Long userId,
                                              @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT *
              FROM career_evidence_usage
             WHERE user_id = #{userId}
               AND usage_key_hash = #{usageKeyHash}
               AND deleted = 0
             LIMIT 1
            """)
    CareerEvidenceUsage selectByUsageKey(@Param("userId") Long userId,
                                         @Param("usageKeyHash") String usageKeyHash);

    @Select("""
            SELECT *
              FROM career_evidence_usage
             WHERE id = #{usageId}
               AND user_id = #{userId}
               AND deleted = 0
             LIMIT 1
            """)
    CareerEvidenceUsage selectOwned(@Param("usageId") Long usageId,
                                    @Param("userId") Long userId);
}
