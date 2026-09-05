package com.codecoachai.task.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class AsyncTaskMapperAdminStatsContractTest {

    @Test
    void failureStatusFilterIsStableAcrossDashboardAndTaskCenter() {
        assertEquals(
                List.of("FAILED", "DEAD", "ERROR", "DEAD_LETTER"),
                AsyncTaskMapper.ADMIN_FAILURE_STATUSES);
        assertEquals(
                "FAILED,DEAD,ERROR,DEAD_LETTER",
                AsyncTaskMapper.ADMIN_FAILURE_STATUS_FILTER);
        assertEquals("Asia/Shanghai", AsyncTaskMapper.ADMIN_STATS_TIMEZONE);
    }

    @Test
    void adminCountExcludesDeletedRowsAndUsesHalfOpenCreatedAtWindow() {
        String sql = selectSql("countAdminTasks");

        assertTrue(sql.contains("where deleted = 0"), sql);
        assertTrue(sql.contains("status in"), sql);
        assertTrue(sql.contains("collection=\"statuses\""), sql);
        assertTrue(sql.contains("created_at &gt;= #{createdfrom}"), sql);
        assertTrue(sql.contains("created_at &lt; #{createdbefore}"), sql);
    }

    private static String selectSql(String methodName) {
        Select annotation = method(methodName).getAnnotation(Select.class);
        if (annotation == null) {
            throw new AssertionError(methodName + " has no @Select contract");
        }
        return String.join(" ", annotation.value())
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Method method(String methodName) {
        return Arrays.stream(AsyncTaskMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing mapper method " + methodName));
    }
}
