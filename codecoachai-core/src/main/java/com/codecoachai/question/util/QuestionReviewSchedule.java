package com.codecoachai.question.util;

import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.domain.enums.MasteryStatusEnum;
import java.time.LocalDateTime;

public final class QuestionReviewSchedule {

    private static final int[] INTERVALS = {1, 3, 7, 15};

    private QuestionReviewSchedule() {
    }

    public static void apply(UserQuestionRecord record, boolean wasInReview) {
        boolean mastered = MasteryStatusEnum.MASTERED.name().equals(record.getMasteryStatus());
        boolean weak = MasteryStatusEnum.NOT_MASTERED.name().equals(record.getMasteryStatus());
        if (!weak && !wasInReview) {
            record.setWrong(0);
            record.setNextReviewAt(null);
            return;
        }
        int stage = record.getReviewStage() == null ? 0
                : Math.max(0, Math.min(record.getReviewStage(), INTERVALS.length));
        stage = mastered ? Math.min(stage + 1, INTERVALS.length) : 0;
        record.setReviewStage(stage);
        record.setWrong(stage < INTERVALS.length ? 1 : 0);
        int days = INTERVALS[Math.min(stage, INTERVALS.length - 1)];
        record.setReviewIntervalDays(days);
        LocalDateTime base = record.getLastAnswerAt() == null ? LocalDateTime.now() : record.getLastAnswerAt();
        record.setNextReviewAt(stage < INTERVALS.length ? base.plusDays(days) : null);
    }
}
