package com.codecoachai.resume.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class UserAbilityProfileMapperContractTest {

    @Test
    void deletedAbilityRowIsRestoredWithOneConditionalUpdate() throws Exception {
        Method method = UserAbilityProfileMapper.class.getMethod(
                "restoreDeletedEvidenceUsageProfile", UserAbilityProfile.class);
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value())
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("UPDATE user_ability_profile"));
        assertTrue(sql.contains("deleted = 0"));
        assertTrue(sql.contains("user_id = #{profile.userId}"));
        assertTrue(sql.contains("skill_code = #{profile.skillCode}"));
        assertTrue(sql.contains("deleted = 1"));
    }
}
