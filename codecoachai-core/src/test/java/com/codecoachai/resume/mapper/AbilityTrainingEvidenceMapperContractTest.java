package com.codecoachai.resume.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class AbilityTrainingEvidenceMapperContractTest {

    @Test
    void completedTrainingAggregateUsesOwnedDoneTasksAndLatestCompletion() throws Exception {
        Method method = AbilityTrainingEvidenceMapper.class.getMethod(
                "selectCompletedSkillAggregates", Long.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value())
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("FROM agent_task"));
        assertTrue(sql.contains("user_id = #{userId}"));
        assertTrue(sql.contains("status = 'DONE'"));
        assertTrue(sql.contains("deleted = 0"));
        assertTrue(sql.contains("COUNT(*) AS evidenceCount"));
        assertTrue(sql.contains("MAX(COALESCE(completed_at, updated_at)) AS lastCompletedAt"));
        assertTrue(sql.contains("GROUP BY NULLIF(TRIM(related_skill_code), '')"));
    }
}
