package com.codecoachai.resume.careeroffer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecisionItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerOfferDecisionItemMapper extends BaseMapper<CareerOfferDecisionItem> {

    @Select("""
            SELECT *
              FROM career_offer_decision_item
             WHERE snapshot_id = #{snapshotId} AND user_id = #{userId} AND deleted = 0
             ORDER BY rank_no ASC, id ASC
            """)
    List<CareerOfferDecisionItem> selectBySnapshot(@Param("snapshotId") Long snapshotId,
                                                   @Param("userId") Long userId);
}
