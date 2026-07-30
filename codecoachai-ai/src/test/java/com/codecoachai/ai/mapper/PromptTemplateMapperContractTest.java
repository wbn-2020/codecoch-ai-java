package com.codecoachai.ai.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class PromptTemplateMapperContractTest {

    @Test
    void activationLocksEveryTemplateForTheSceneInStableOrder() throws Exception {
        Method method = PromptTemplateMapper.class.getMethod(
                "lockSceneTemplatesForActivation", String.class);
        String sql = normalize(String.join(" ", method.getAnnotation(Select.class).value()));

        assertAll(
                () -> assertTrue(List.class.isAssignableFrom(method.getReturnType())),
                () -> assertTrue(sql.contains("where scene = #{scene}")),
                () -> assertTrue(sql.contains("deleted = 0")),
                () -> assertTrue(sql.contains("order by id")),
                () -> assertTrue(sql.endsWith("for update")));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
