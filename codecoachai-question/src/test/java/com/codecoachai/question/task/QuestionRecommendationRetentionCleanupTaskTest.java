package com.codecoachai.question.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class QuestionRecommendationRetentionCleanupTaskTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void cleanupTaskBackfillsLegacyRecommendationRequestPayloadsToMinimizedSnapshotsBeforeExpiryScrub()
            throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        Map<String, Object> legacyRow = new LinkedHashMap<>();
        legacyRow.put("id", 601L);
        legacyRow.put("user_id", 77L);
        legacyRow.put("source_type", "JD_GAP");
        legacyRow.put("source_id", 903L);
        legacyRow.put("question_count", 2);
        legacyRow.put("strategy", "GAP_PRIORITY");
        legacyRow.put("skill_profile_id", 903L);
        legacyRow.put("study_plan_id", null);
        legacyRow.put("request_json", """
                {
                  "batchId": 601,
                  "userId": 77,
                  "sourceType": "JD_GAP",
                  "sourceId": 903,
                  "questionCount": 2,
                  "difficultyPreference": "HARD",
                  "strategy": "GAP_PRIORITY",
                  "skillProfileId": 903,
                  "targetJobJson": "{\\"jobTitle\\":\\"Java backend\\",\\"contact\\":\\"secret@example.com\\"}",
                  "skillProfileJson": "{\\"profileName\\":\\"Gap Focus\\",\\"phone\\":\\"13812345678\\"}",
                  "skillGapsJson": "[{\\"id\\":7701,\\"skillName\\":\\"Redis\\"},{\\"id\\":7702,\\"skillName\\":\\"Kafka\\"}]"
                }
                """);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(
                List.of(legacyRow),
                List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setInt(task, "scanLimit", 25);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        invoke(task, "cleanupExpiredRequestFields");

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForList(
                querySqlCaptor.capture(),
                queryArgsCaptor.capture(),
                any(int[].class));
        String legacyQuerySql = querySqlCaptor.getAllValues().get(0);
        Object[] legacyQueryArgs = queryArgsCaptor.getAllValues().get(0);
        assertTrue(legacyQuerySql.contains("FROM question_recommendation_batch"));
        assertTrue(legacyQuerySql.contains("request_json LIKE"));
        assertLegacyPayloadFilter(legacyQuerySql);
        assertEquals(1, legacyQueryArgs.length);
        assertEquals(25, legacyQueryArgs[0]);

        String scrubQuerySql = querySqlCaptor.getAllValues().get(1);
        assertTrue(scrubQuerySql.contains("updated_at <= ?"));
        assertTrue(scrubQuerySql.contains("status IN (?, ?)"));
        assertLegacyPayloadFilter(scrubQuerySql);

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(
                updateSqlCaptor.capture(),
                updateArgsCaptor.capture(),
                any(int[].class));
        String updateSql = updateSqlCaptor.getAllValues().get(0);
        Object[] updateArgs = updateArgsCaptor.getAllValues().get(0);
        assertTrue(updateSql.contains("UPDATE question_recommendation_batch"));
        assertTrue(updateSql.contains("request_json = ?"));
        assertTrue(updateSql.contains("source_type = ?"));
        assertTrue(updateSql.contains("source_id = ?"));
        assertTrue(updateSql.contains("match_report_id = ?"));
        assertTrue(updateSql.contains("skill_profile_id = ?"));
        assertTrue(updateSql.contains("study_plan_id = ?"));
        assertTrue(updateSql.contains("strategy = ?"));
        assertTrue(updateSql.contains("question_count = ?"));
        assertFalse(updateSql.contains("request_json = NULL"));
        assertEquals(9, updateArgs.length);
        assertEquals("JD_GAP", updateArgs[0]);
        assertEquals(903L, updateArgs[1]);
        assertEquals(null, updateArgs[2]);
        assertEquals(903L, updateArgs[3]);
        assertEquals(null, updateArgs[4]);
        assertEquals("GAP_PRIORITY", updateArgs[5]);
        assertEquals(2, updateArgs[6]);
        assertEquals(601L, updateArgs[8]);

        JsonNode minimized = OBJECT_MAPPER.readTree(String.valueOf(updateArgs[7]));
        assertEquals("MINIMIZED_METADATA", minimized.path("storageMode").asText());
        assertEquals(601L, minimized.path("batchId").asLong());
        assertEquals(77L, minimized.path("userId").asLong());
        assertEquals("JD_GAP", minimized.path("sourceType").asText());
        assertEquals(903L, minimized.path("sourceId").asLong());
        assertEquals(2, minimized.path("questionCount").asInt());
        assertEquals("HARD", minimized.path("difficultyPreference").asText());
        assertEquals("GAP_PRIORITY", minimized.path("strategy").asText());
        assertEquals(903L, minimized.path("skillProfileId").asLong());
        assertTrue(minimized.path("questionRecommendationRequestStored").asBoolean());
        assertEquals(2, minimized.path("gapItemIds").size());
        assertEquals(7701L, minimized.path("gapItemIds").get(0).asLong());
        assertEquals(7702L, minimized.path("gapItemIds").get(1).asLong());
        assertFalse(String.valueOf(updateArgs[7]).contains("secret@example.com"));
        assertFalse(String.valueOf(updateArgs[7]).contains("13812345678"));
        assertFalse(minimized.has("targetJobJson"));
        assertFalse(minimized.has("skillProfileJson"));
        assertFalse(minimized.has("skillGapsJson"));
    }

    @Test
    void cleanupTaskBackfillsSparseStudyPlanBatchMetadataAlongsideMinimizedSnapshot()
            throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        Map<String, Object> legacyRow = new LinkedHashMap<>();
        legacyRow.put("id", 602L);
        legacyRow.put("user_id", 77L);
        legacyRow.put("source_type", null);
        legacyRow.put("source_id", null);
        legacyRow.put("match_report_id", null);
        legacyRow.put("question_count", null);
        legacyRow.put("strategy", null);
        legacyRow.put("skill_profile_id", null);
        legacyRow.put("study_plan_id", null);
        legacyRow.put("request_json", """
                {
                  "batchId": 602,
                  "userId": 77,
                  "sourceType": "STUDY_PLAN",
                  "sourceId": 9901,
                  "matchReportId": 902,
                  "questionCount": 3,
                  "difficultyPreference": "MEDIUM",
                  "strategy": "GAP_PRIORITY",
                  "skillProfileId": 903,
                  "studyPlanId": 9901,
                  "studyPlanJson": "{\\"planTitle\\":\\"Redis repair\\",\\"contact\\":\\"secret@example.com\\"}",
                  "studyTasksJson": "[{\\"taskId\\":1,\\"phone\\":\\"13812345678\\"}]"
                }
                """);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(
                List.of(legacyRow),
                List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setInt(task, "scanLimit", 25);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        invoke(task, "cleanupExpiredRequestFields");

        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(
                anyString(),
                updateArgsCaptor.capture(),
                any(int[].class));
        Object[] updateArgs = updateArgsCaptor.getAllValues().get(0);

        assertEquals(9, updateArgs.length);
        assertEquals("STUDY_PLAN", updateArgs[0]);
        assertEquals(9901L, updateArgs[1]);
        assertEquals(902L, updateArgs[2]);
        assertEquals(903L, updateArgs[3]);
        assertEquals(9901L, updateArgs[4]);
        assertEquals("GAP_PRIORITY", updateArgs[5]);
        assertEquals(3, updateArgs[6]);
        assertEquals(602L, updateArgs[8]);

        JsonNode minimized = OBJECT_MAPPER.readTree(String.valueOf(updateArgs[7]));
        assertEquals("MINIMIZED_METADATA", minimized.path("storageMode").asText());
        assertEquals(602L, minimized.path("batchId").asLong());
        assertEquals(77L, minimized.path("userId").asLong());
        assertEquals("STUDY_PLAN", minimized.path("sourceType").asText());
        assertEquals(9901L, minimized.path("sourceId").asLong());
        assertEquals(3, minimized.path("questionCount").asInt());
        assertEquals("MEDIUM", minimized.path("difficultyPreference").asText());
        assertEquals("GAP_PRIORITY", minimized.path("strategy").asText());
        assertEquals(903L, minimized.path("skillProfileId").asLong());
        assertEquals(9901L, minimized.path("studyPlanId").asLong());
        assertTrue(minimized.path("questionRecommendationRequestStored").asBoolean());
        assertFalse(String.valueOf(updateArgs[7]).contains("secret@example.com"));
        assertFalse(String.valueOf(updateArgs[7]).contains("13812345678"));
        assertFalse(minimized.has("studyPlanJson"));
        assertFalse(minimized.has("studyTasksJson"));
    }

    @Test
    void cleanupTaskNullsExpiredLegacyRichRequestPayloadButPreservesResultMetadata() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(
                List.of(),
                List.of(
                        Map.of("id", 501L),
                        Map.of("id", 502L)
                ));
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(2);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setInt(task, "scanLimit", 25);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        LocalDateTime beforeLowerBound = LocalDateTime.now().minusDays(31);
        invoke(task, "cleanupExpiredRequestFields");
        LocalDateTime afterUpperBound = LocalDateTime.now().minusDays(29);

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).queryForList(querySqlCaptor.capture(), queryArgsCaptor.capture(), any(int[].class));
        String querySql = querySqlCaptor.getAllValues().get(1);
        assertTrue(querySql.contains("FROM question_recommendation_batch"));
        assertTrue(querySql.contains("updated_at <= ?"));
        assertTrue(querySql.contains("status IN (?, ?)"));
        assertTrue(querySql.contains("request_json IS NOT NULL"));
        assertTrue(querySql.contains("request_json <> ''"));
        assertLegacyPayloadFilter(querySql);
        assertFalse(querySql.contains("result_json"));
        assertTrue(querySql.contains("LIMIT ?"));

        Object[] queryArgs = queryArgsCaptor.getAllValues().get(1);
        assertEquals(4, queryArgs.length);
        Timestamp cutoff = assertInstanceOf(Timestamp.class, queryArgs[0]);
        assertTrue(cutoff.toLocalDateTime().isAfter(beforeLowerBound));
        assertTrue(cutoff.toLocalDateTime().isBefore(afterUpperBound));
        assertEquals("SUCCESS", queryArgs[1]);
        assertEquals("FAILED", queryArgs[2]);
        assertEquals(25, queryArgs[3]);

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(updateSqlCaptor.capture(), updateArgsCaptor.capture(), any(int[].class));
        String updateSql = updateSqlCaptor.getValue();
        assertTrue(updateSql.contains("UPDATE question_recommendation_batch"));
        assertTrue(updateSql.contains("request_json = NULL"));
        assertFalse(updateSql.contains("result_json = NULL"));
        assertFalse(updateSql.contains("error_message = NULL"));
        assertTrue(updateSql.contains("id IN (?, ?)"));

        Object[] updateArgs = updateArgsCaptor.getValue();
        assertEquals(2, updateArgs.length);
        assertEquals(501L, updateArgs[0]);
        assertEquals(502L, updateArgs[1]);
    }

    @Test
    void cleanupTaskDoesNotQueryOrUpdateWhenLockIsNotAcquired() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenReturn(false);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);

        invoke(task, "cleanupExpiredRequestFields");

        verify(lockHelper).tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class));
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class), any(int[].class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
        verifyNoInteractions(jdbcTemplate);
    }

    private static Object newTask(JdbcTemplate jdbcTemplate,
                                  DistributedLockHelper lockHelper) throws Exception {
        Class<?> type = Class.forName("com.codecoachai.question.task.QuestionRecommendationRetentionCleanupTask");
        return type.getDeclaredConstructor(JdbcTemplate.class, DistributedLockHelper.class, ObjectMapper.class)
                .newInstance(jdbcTemplate, lockHelper, OBJECT_MAPPER);
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setLong(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void assertLegacyPayloadFilter(String sql) {
        assertTrue(sql.contains("targetJobJson"));
        assertTrue(sql.contains("matchReportJson"));
        assertTrue(sql.contains("skillProfileJson"));
        assertTrue(sql.contains("skillGapsJson"));
        assertTrue(sql.contains("studyPlanJson"));
        assertTrue(sql.contains("studyTasksJson"));
    }
}
