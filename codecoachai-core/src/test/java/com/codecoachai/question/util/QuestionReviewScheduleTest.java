package com.codecoachai.question.util;

import com.codecoachai.question.domain.entity.UserQuestionRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionReviewScheduleTest {
    @Test
    void nullReviewDateIsIncludedInUpdateSql() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var table = com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""), UserQuestionRecord.class);
        var field = table.getFieldList().stream()
                .filter(item -> "nextReviewAt".equals(item.getProperty())).findFirst().orElseThrow();
        String sql = field.getSqlSet("et.");
        assertTrue(sql.contains("next_review_at"));
        assertFalse(sql.contains("<if"));
    }

    @Test
    void preservesOneLegacyTransitionThenInitializesFsrsForStoredRecords() {
        UserQuestionRecord record = new UserQuestionRecord();
        record.setLastAnswerAt(LocalDateTime.of(2026, 9, 10, 10, 0));
        record.setReviewStage(0);
        record.setReviewIntervalDays(1);
        record.setMasteryStatus("MASTERED");

        QuestionReviewSchedule.apply(record, true);

        assertEquals(1, record.getWrong());
        assertEquals(3, record.getReviewIntervalDays());
        assertEquals(record.getLastAnswerAt().plusDays(3), record.getNextReviewAt());
        assertEquals(3.0, record.getMemoryStability());
        assertEquals(5.0, record.getMemoryDifficulty());
        assertEquals(1, record.getReviewReps());
        assertEquals(0, record.getReviewLapses());

        QuestionReviewSchedule.apply(record, true);
        assertTrue(record.getMemoryStability() > 3.0);
        assertEquals(2, record.getReviewReps());
    }

    @Test
    void newWrongRecordStartsFsrsAndKeepsReviewQueueUntilMemoryIsStable() {
        UserQuestionRecord record = new UserQuestionRecord();
        record.setLastAnswerAt(LocalDateTime.of(2026, 9, 10, 10, 0));
        record.setMasteryStatus("NOT_MASTERED");

        QuestionReviewSchedule.apply(record, false);

        assertEquals(1, record.getWrong());
        assertEquals(1, record.getReviewIntervalDays());
        assertEquals(1.0, record.getMemoryStability());
        assertEquals(5.0, record.getMemoryDifficulty());
        assertEquals(1, record.getReviewLapses());

        record.setMasteryStatus("MASTERED");
        for (int i = 0; i < 12 && record.getWrong() == 1; i++) {
            QuestionReviewSchedule.apply(record, true);
        }
        assertEquals(0, record.getWrong());
        assertNull(record.getNextReviewAt());
        assertTrue(record.getReviewReps() >= 4);
    }

    @Test
    void firstCorrectAnswerDoesNotCreateReviewAndFailureResetsStage() {
        UserQuestionRecord record = new UserQuestionRecord();
        record.setMasteryStatus("MASTERED");
        QuestionReviewSchedule.apply(record, false);
        assertEquals(0, record.getWrong());
        assertNull(record.getNextReviewAt());
        record.setReviewStage(3);
        record.setMasteryStatus("NOT_MASTERED");
        QuestionReviewSchedule.apply(record, true);
        assertEquals(0, record.getReviewStage());
        assertEquals(1, record.getReviewIntervalDays());
        assertEquals(1, record.getWrong());
    }
}
