package com.codecoachai.resume.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EvidenceUsageAbilityProjectionMapper {

    @Select("""
            SELECT skill_code
              FROM evidence_usage_ability_projection
             WHERE result_id = #{resultId}
               AND user_id = #{userId}
             ORDER BY skill_code
            """)
    List<String> selectSkillCodes(@Param("resultId") Long resultId,
                                  @Param("userId") Long userId);

    @Delete("""
            <script>
            DELETE FROM evidence_usage_ability_projection
             WHERE result_id = #{resultId}
               AND user_id = #{userId}
               AND skill_code IN
               <foreach collection="skillCodes" item="skillCode"
                        open="(" separator="," close=")">
                 #{skillCode}
               </foreach>
            </script>
            """)
    int deleteSkillCodes(@Param("resultId") Long resultId,
                         @Param("userId") Long userId,
                         @Param("skillCodes") List<String> skillCodes);

    @Insert("""
            <script>
            INSERT IGNORE INTO evidence_usage_ability_projection (
                result_id, usage_id, user_id, skill_code, created_at, updated_at
            ) VALUES
            <foreach collection="skillCodes" item="skillCode" separator=",">
                (#{resultId}, #{usageId}, #{userId}, #{skillCode}, NOW(), NOW())
            </foreach>
            </script>
            """)
    int insertSkillCodes(@Param("resultId") Long resultId,
                         @Param("usageId") Long usageId,
                         @Param("userId") Long userId,
                         @Param("skillCodes") List<String> skillCodes);

    @Select("""
            SELECT COUNT(DISTINCT usage_id)
              FROM evidence_usage_ability_projection
             WHERE user_id = #{userId}
               AND skill_code = #{skillCode}
            """)
    long countDistinctUsageBySkillCode(@Param("userId") Long userId,
                                       @Param("skillCode") String skillCode);

    @Select("""
            <script>
            SELECT skill_code AS skillCode,
                   COUNT(DISTINCT usage_id) AS usageCount,
                   MAX(updated_at) AS lastProjectedAt
              FROM evidence_usage_ability_projection
             WHERE user_id = #{userId}
               AND skill_code IN
               <foreach collection="skillCodes" item="skillCode"
                        open="(" separator="," close=")">
                 #{skillCode}
               </foreach>
             GROUP BY skill_code
             ORDER BY skill_code
            </script>
            """)
    List<SkillUsageAggregate> selectUsageAggregates(
            @Param("userId") Long userId,
            @Param("skillCodes") List<String> skillCodes);

    @Data
    class SkillUsageAggregate {
        private String skillCode;
        private Long usageCount;
        private LocalDateTime lastProjectedAt;
    }
}
