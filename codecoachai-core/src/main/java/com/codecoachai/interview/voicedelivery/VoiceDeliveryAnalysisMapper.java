package com.codecoachai.interview.voicedelivery;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VoiceDeliveryAnalysisMapper extends BaseMapper<VoiceDeliveryAnalysis> {

    @Select("""
            <script>
            SELECT analysis.*
            FROM interview_voice_delivery_analysis analysis
            INNER JOIN (
                SELECT session_id, MAX(id) AS latest_id
                FROM interview_voice_delivery_analysis
                WHERE user_id = #{userId}
                  AND deleted = 0
                  AND session_id IN
                  <foreach collection="sessionIds" item="sessionId" open="(" separator="," close=")">
                    #{sessionId}
                  </foreach>
                GROUP BY session_id
            ) latest ON latest.latest_id = analysis.id
            WHERE analysis.user_id = #{userId}
              AND analysis.deleted = 0
            ORDER BY analysis.session_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<VoiceDeliveryAnalysis> selectLatestBySessions(
            @Param("userId") Long userId,
            @Param("sessionIds") List<Long> sessionIds,
            @Param("limit") int limit);
}
