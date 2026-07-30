package com.codecoachai.ai.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class PromptTemplateVersionMapperContractTest {

    @Test
    void fallbackQueryOnlyReturnsVersionsOwnedByEnabledTemplates() throws Exception {
        Method method = PromptTemplateVersionMapper.class.getMethod(
                "selectActiveVersionOwnedByEnabledTemplate", String.class);
        String sql = normalize(String.join(" ", method.getAnnotation(Select.class).value()));

        assertAll(
                () -> assertTrue(sql.contains("inner join prompt_template t")),
                () -> assertTrue(sql.contains("t.id = v.template_id")),
                () -> assertTrue(sql.contains("t.deleted = 0")),
                () -> assertTrue(sql.contains("t.status = 1")),
                () -> assertTrue(sql.contains("(t.enabled = 1 or t.enabled is null)")),
                () -> assertTrue(sql.contains("v.status = 'active'")),
                () -> assertTrue(sql.contains("v.is_active = 1")));
    }

    @Test
    void activationUpdateDeactivatesOtherActiveVersionsForTheScene() throws Exception {
        Method method = PromptTemplateVersionMapper.class.getMethod(
                "deactivateOtherActiveVersionsForScene", String.class, Long.class);
        String sql = normalize(String.join(" ", method.getAnnotation(Update.class).value()));

        assertAll(
                () -> assertTrue(sql.contains("where scene = #{scene}")),
                () -> assertTrue(sql.contains("id <> #{activeversionid}")),
                () -> assertTrue(sql.contains("(status = 'active' or is_active = 1)")),
                () -> assertTrue(sql.contains("status = 'inactive'")),
                () -> assertTrue(sql.contains("is_active = 0")));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
