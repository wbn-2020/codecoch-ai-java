package com.codecoachai.interview.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class InterviewReportReceiptAndAnswerTimingMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("..", "sql", "migration");
    private static final String MIGRATION_NAME =
            "V4_107__interview_report_receipt_and_answer_timing.sql";

    @Test
    void migrationChecksEveryAddedColumnIndependently() throws Exception {
        try (var paths = Files.list(MIGRATION_DIR)) {
            List<Path> matches = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_107__"))
                    .toList();
            assertEquals(1, matches.size());
            assertEquals(MIGRATION_NAME, matches.get(0).getFileName().toString());
        }

        String sql = Files.readString(MIGRATION_DIR.resolve(MIGRATION_NAME))
                .toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();

        for (String column : List.of(
                "question_presented_at",
                "answer_duration_seconds",
                "async_message_id",
                "async_trace_id",
                "async_biz_type",
                "async_biz_id",
                "async_send_status",
                "async_dispatch_mode")) {
            assertEquals(1, occurrences(sql, "column_name = '" + column + "'"), column);
            assertEquals(1, occurrences(sql, "add column " + column + " "), column);
        }
        assertTrue(occurrences(sql, "'select 1'") >= 8, sql);
    }

    private int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
