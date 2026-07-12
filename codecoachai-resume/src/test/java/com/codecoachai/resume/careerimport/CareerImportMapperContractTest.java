package com.codecoachai.resume.careerimport;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportDedupeGuardMapper;
import java.time.LocalDateTime;
import java.util.Locale;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class CareerImportMapperContractTest {

    @Test
    void guardUsesInsertIgnoreAndRowLock() throws Exception {
        String insertSql = sql(CareerImportDedupeGuardMapper.class
                .getMethod("insertIgnore", Long.class, String.class)
                .getAnnotation(Insert.class).value());
        String lockSql = sql(CareerImportDedupeGuardMapper.class
                .getMethod("selectForUpdate", Long.class, String.class)
                .getAnnotation(Select.class).value());

        assertTrue(insertSql.contains("insert ignore into career_import_dedupe_guard"));
        assertTrue(lockSql.contains("from career_import_dedupe_guard"));
        assertTrue(lockSql.contains("for update"));
    }

    @Test
    void freshCandidateQueriesUseOneThousandAndOneRowSentinel() throws Exception {
        String datedSql = sql(JobApplicationMapper.class
                .getMethod(
                        "selectCareerImportCandidatesInDateWindow",
                        Long.class,
                        LocalDateTime.class,
                        LocalDateTime.class)
                .getAnnotation(Select.class).value());
        String undatedSql = sql(JobApplicationMapper.class
                .getMethod("selectCareerImportCandidatesForUndated", Long.class)
                .getAnnotation(Select.class).value());

        assertTrue(datedSql.contains("applied_at between"));
        assertTrue(datedSql.contains("or applied_at is null"));
        assertTrue(datedSql.contains("limit 1001"));
        assertTrue(undatedSql.contains("where user_id ="));
        assertTrue(undatedSql.contains("deleted = 0"));
        assertTrue(undatedSql.contains("limit 1001"));
    }

    @Test
    void icsWinnerReadUsesBinaryUidAndLockingCurrentRead() throws Exception {
        String winnerSql = sql(CareerCalendarEventMapper.class
                .getMethod(
                        "selectActiveByExternalUidBinaryForUpdate",
                        Long.class,
                        String.class)
                .getAnnotation(Select.class).value());

        assertTrue(winnerSql.contains("from career_calendar_event"), winnerSql);
        assertTrue(winnerSql.contains("user_id ="), winnerSql);
        assertTrue(winnerSql.contains("binary external_uid = binary"), winnerSql);
        assertTrue(winnerSql.contains("deleted = 0"), winnerSql);
        assertTrue(winnerSql.contains("limit 1 for update"), winnerSql);
    }

    private static String sql(String[] fragments) {
        return String.join(" ", fragments)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
