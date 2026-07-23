package com.codecoachai.resume.careeroffer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecisionSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CareerOfferDecisionSnapshotMapper extends BaseMapper<CareerOfferDecisionSnapshot> {

    @Select("""
            SELECT *
              FROM career_offer_decision_snapshot
             WHERE id = #{snapshotId} AND user_id = #{userId} AND deleted = 0
            """)
    CareerOfferDecisionSnapshot selectOwned(@Param("snapshotId") Long snapshotId,
                                            @Param("userId") Long userId);
}
