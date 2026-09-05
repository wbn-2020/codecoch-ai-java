package com.codecoachai.resume.careercontact.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careercontact.entity.CareerInterviewRoundContact;
import com.codecoachai.resume.careercontact.vo.CareerInterviewRoundContactVO;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerInterviewRoundContactMapper extends BaseMapper<CareerInterviewRoundContact> {
    @Select("""
            SELECT id FROM career_interview_round
             WHERE id = #{roundId} AND user_id = #{userId} AND deleted = 0
             LIMIT 1
            """)
    Long selectOwnedRound(@Param("userId") Long userId, @Param("roundId") Long roundId);

    @Select("""
            SELECT r.id, r.interview_round_id, r.contact_id, c.display_name,
                   c.role_type, r.relationship_type, r.created_at
              FROM career_interview_round_contact r
              JOIN career_contact c ON c.id = r.contact_id
             WHERE r.user_id = #{userId} AND r.interview_round_id = #{roundId}
               AND r.deleted = 0 AND c.deleted = 0
             ORDER BY r.id ASC
            """)
    List<CareerInterviewRoundContactVO> selectViews(@Param("userId") Long userId,
                                                    @Param("roundId") Long roundId);
}
