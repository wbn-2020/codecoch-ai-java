package com.codecoachai.resume.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class EvidenceUsageAbilityProjectionMapperContractTest {

    @Test
    void countUsesDistinctUsageInsteadOfRawResultRows() throws Exception {
        Method method = EvidenceUsageAbilityProjectionMapper.class.getMethod(
                "countDistinctUsageBySkillCode", Long.class, String.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("COUNT(DISTINCT usage_id)"));
        assertTrue(sql.contains("user_id = #{userId}"));
        assertTrue(sql.contains("skill_code = #{skillCode}"));
    }

    @Test
    void contributionInsertIsIdempotentPerResultAndSkill() throws Exception {
        Method method = EvidenceUsageAbilityProjectionMapper.class.getMethod(
                "insertSkillCodes", Long.class, Long.class, Long.class, java.util.List.class);
        Insert insert = method.getAnnotation(Insert.class);
        String sql = String.join(" ", insert.value());

        assertTrue(sql.contains("INSERT IGNORE INTO evidence_usage_ability_projection"));
        assertTrue(sql.contains("result_id, usage_id, user_id, skill_code"));
    }

    @Test
    void abilityMapAggregateUsesDistinctUsageLedgerCounts() throws Exception {
        Method method = EvidenceUsageAbilityProjectionMapper.class.getMethod(
                "selectUsageAggregates", Long.class, java.util.List.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("COUNT(DISTINCT usage_id) AS usageCount"));
        assertTrue(sql.contains("MAX(updated_at) AS lastProjectedAt"));
        assertTrue(sql.contains("GROUP BY skill_code"));
    }
}
