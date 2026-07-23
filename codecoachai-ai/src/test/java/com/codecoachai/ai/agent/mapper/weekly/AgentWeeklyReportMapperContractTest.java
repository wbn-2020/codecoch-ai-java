package com.codecoachai.ai.agent.mapper.weekly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class AgentWeeklyReportMapperContractTest {

    @Test
    void mainlineMapperSignaturesRemainAvailable() throws Exception {
        method(
                AgentWeeklyReportMapper.class,
                "ensureIdentity",
                Long.class,
                Long.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class);
        method(
                AgentWeeklyReportMapper.class,
                "selectIdentity",
                Long.class,
                LocalDate.class,
                String.class,
                String.class);
        method(AgentWeeklyReportMapper.class, "selectOwned", Long.class, Long.class);
        method(
                AgentWeeklyReportMapper.class,
                "selectIdentityForUpdate",
                Long.class,
                LocalDate.class,
                String.class,
                String.class);
        method(
                AgentWeeklyReportMapper.class,
                "updateCurrentSnapshot",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                Integer.class,
                String.class,
                String.class,
                Integer.class,
                String.class);
        method(
                AgentWeeklyReportMapper.class,
                "claimGeneration",
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class);
        method(
                AgentWeeklyReportMapper.class,
                "clearGenerationClaim",
                Long.class,
                Long.class,
                String.class);
        method(
                AgentWeeklyReportMapper.class,
                "selectHistoryIdentities",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                LocalDate.class,
                Integer.class);

        method(
                AgentWeeklyReportSnapshotMapper.class,
                "insertSnapshot",
                AgentWeeklyReportSnapshot.class);
        method(
                AgentWeeklyReportSnapshotMapper.class,
                "selectByIdempotencyKey",
                Long.class,
                String.class);
        method(
                AgentWeeklyReportSnapshotMapper.class,
                "selectByGenerationFingerprint",
                Long.class,
                Long.class,
                String.class);
        method(
                AgentWeeklyReportSnapshotMapper.class,
                "selectOwnedSnapshot",
                Long.class,
                Long.class,
                Long.class);
        method(
                AgentWeeklyReportSnapshotMapper.class,
                "selectHistory",
                Long.class,
                Long.class);
        method(
                AgentWeeklyReportSnapshotMapper.class,
                "selectMaxVersion",
                Long.class,
                Long.class);
        method(
                AgentWeeklyReportSnapshotMapper.class,
                "selectComparableCurrentSnapshots",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDateTime.class,
                String.class,
                String.class,
                Integer.class);

        method(AgentWeeklyReportSourceMapper.class, "insertBatch", List.class);
        method(
                AgentWeeklyReportSourceMapper.class,
                "selectBySnapshot",
                Long.class,
                Long.class);
    }

    @Test
    void entitiesAndMappersDoNotExposeGenericUpdateOrDeletePaths() {
        for (Class<?> mapper : List.of(
                AgentWeeklyReportMapper.class,
                AgentWeeklyReportSnapshotMapper.class,
                AgentWeeklyReportSourceMapper.class)) {
            assertFalse(BaseMapper.class.isAssignableFrom(mapper), mapper.getName());
            for (Method method : mapper.getDeclaredMethods()) {
                assertFalse(method.isAnnotationPresent(Delete.class), method.toString());
            }
        }

        for (Class<?> mapper : List.of(
                AgentWeeklyReportSnapshotMapper.class,
                AgentWeeklyReportSourceMapper.class)) {
            for (Method method : mapper.getDeclaredMethods()) {
                assertFalse(method.isAnnotationPresent(Update.class), method.toString());
                assertTrue(
                        method.isAnnotationPresent(Insert.class)
                                || method.isAnnotationPresent(Select.class),
                        method.toString());
            }
        }

        assertEquals(Object.class, AgentWeeklyReport.class.getSuperclass());
        assertEquals(Object.class, AgentWeeklyReportSnapshot.class.getSuperclass());
        assertEquals(Object.class, AgentWeeklyReportSource.class.getSuperclass());
    }

    @Test
    void currentAndComparableSnapshotJoinsValidateOwnerAndParentIdentity()
            throws Exception {
        String current = selectSql(
                AgentWeeklyReportMapper.class,
                "selectHistoryIdentities",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                LocalDate.class,
                Integer.class);
        assertTrue(
                current.contains("current_snapshot.user_id = report.user_id"), current);
        assertTrue(
                current.contains("current_snapshot.weekly_report_id = report.id"), current);
        assertTrue(
                current.contains("current_snapshot.user_id = #{userid}"), current);
        assertTrue(current.contains("current_snapshot.deleted = 0"), current);
        assertTrue(current.contains("report.user_id = #{userid}"), current);
        assertTrue(current.contains("report.deleted = 0"), current);

        String comparable = selectSql(
                AgentWeeklyReportSnapshotMapper.class,
                "selectComparableCurrentSnapshots",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDateTime.class,
                String.class,
                String.class,
                Integer.class);
        assertTrue(comparable.contains("s.user_id = r.user_id"), comparable);
        assertTrue(comparable.contains("s.weekly_report_id = r.id"), comparable);
        assertTrue(comparable.contains("s.user_id = #{userid}"), comparable);
        assertTrue(comparable.contains("r.user_id = #{userid}"), comparable);
        assertTrue(comparable.contains("s.deleted = 0"), comparable);
        assertTrue(comparable.contains("r.deleted = 0"), comparable);
    }

    @Test
    void reportReadsAlwaysRequireOwnerAndLiveRows() {
        for (Method method : AgentWeeklyReportMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String sql = sql(select.value());
            assertTrue(sql.contains("#{userid}"), method + ": " + sql);
            assertTrue(
                    sql.contains("deleted = 0")
                            || sql.contains("report.deleted = 0"),
                    method + ": " + sql);
        }
    }

    @Test
    void snapshotAndSourceReadsRequireOwnerDeletionAndParentRows()
            throws Exception {
        for (Method method : AgentWeeklyReportSnapshotMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String sql = sql(select.value());
            assertTrue(sql.contains("#{userid}"), method + ": " + sql);
            assertTrue(
                    sql.contains("snapshot.deleted = 0")
                            || sql.contains("s.deleted = 0"),
                    method + ": " + sql);
            assertTrue(
                    sql.contains("report.deleted = 0")
                            || sql.contains("r.deleted = 0"),
                    method + ": " + sql);
            assertTrue(
                    sql.contains("report.id = snapshot.weekly_report_id")
                            || sql.contains("s.weekly_report_id = r.id"),
                    method + ": " + sql);
            assertTrue(
                    sql.contains("report.user_id = snapshot.user_id")
                            || sql.contains("s.user_id = r.user_id"),
                    method + ": " + sql);
        }

        String source = selectSql(
                AgentWeeklyReportSourceMapper.class,
                "selectBySnapshot",
                Long.class,
                Long.class);
        assertTrue(source.contains("source.user_id = #{userid}"), source);
        assertTrue(source.contains("source.deleted = 0"), source);
        assertTrue(source.contains("snapshot.id = source.snapshot_id"), source);
        assertTrue(source.contains("snapshot.user_id = source.user_id"), source);
        assertTrue(source.contains("snapshot.user_id = #{userid}"), source);
        assertTrue(source.contains("snapshot.deleted = 0"), source);
        assertTrue(source.contains("report.id = snapshot.weekly_report_id"), source);
        assertTrue(source.contains("report.user_id = snapshot.user_id"), source);
        assertTrue(source.contains("report.deleted = 0"), source);
    }

    @Test
    void currentPointerUpdateCannotAttachAnotherOwnerOrParentSnapshot()
            throws Exception {
        Update update = method(
                        AgentWeeklyReportMapper.class,
                        "updateCurrentSnapshot",
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        Integer.class,
                        String.class,
                        String.class,
                        Integer.class,
                        String.class)
                .getAnnotation(Update.class);
        assertNotNull(update);
        String sql = sql(update.value());
        assertTrue(sql.contains("snapshot.id = #{snapshotid}"), sql);
        assertTrue(sql.contains("snapshot.user_id = #{userid}"), sql);
        assertTrue(
                sql.contains("snapshot.weekly_report_id = agent_weekly_report.id"), sql);
        assertTrue(sql.contains("snapshot.deleted = 0"), sql);
    }

    private static Method method(
            Class<?> mapperType,
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        return mapperType.getMethod(methodName, parameterTypes);
    }

    private static String selectSql(
            Class<?> mapperType,
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        Select select = method(mapperType, methodName, parameterTypes)
                .getAnnotation(Select.class);
        assertNotNull(select);
        return sql(select.value());
    }

    private static String sql(String[] fragments) {
        return String.join(" ", fragments)
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
