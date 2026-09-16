package com.codecoachai.question.util;

import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.domain.enums.MasteryStatusEnum;
import com.codecoachai.question.support.ReviewScheduler;
import java.time.LocalDateTime;

/**
 * 错题复习调度入口：把 {@link ReviewScheduler}（FSRS-lite 连续记忆模型）的结果写回
 * user_question_record 的新旧两套列。
 *
 * <p>路径选择：
 * <ul>
 *   <li>模型已初始化（memory_stability 非 NULL）：走 FSRS-lite，interval 由稳定性推导，
 *       旧列 review_interval_days / review_stage 同步为"最近档位"映射，保证前端旧逻辑不坏。</li>
 *   <li>存量记录（stability=NULL）且尚未进入队列：答错时初始化冷启动参数（S=1.0/D=5.0，
 *       interval=1 天，与旧行为一致）；答对不入队则保持旧语义（清零调度）。</li>
     *   <li>存量记录（stability=NULL）且已在队列（wasInReview）：先按旧档位推进一次（本次间隔
     *       与升级前一致），同时用该档位天数初始化模型，下次复习走 FSRS-lite。</li>
 * </ul>
 */
public final class QuestionReviewSchedule {

    private static final int[] INTERVALS = ReviewScheduler.LEGACY_INTERVALS;

    private QuestionReviewSchedule() {
    }

    public static void apply(UserQuestionRecord record, boolean wasInReview) {
        boolean mastered = MasteryStatusEnum.MASTERED.name().equals(record.getMasteryStatus());
        boolean weak = MasteryStatusEnum.NOT_MASTERED.name().equals(record.getMasteryStatus());
        ReviewScheduler.MemoryState state = memoryStateOf(record);
        boolean modelInitialized = state != null && state.initialized();
        if (!weak && !wasInReview) {
            // 从未/不再属于错题队列：退出复习并清空调度（模型参数保留，不物理清除）。
            record.setWrong(0);
            record.setNextReviewAt(null);
            return;
        }
        if (!modelInitialized) {
            if (wasInReview) {
                // 存量记录 fallback：本次仍按旧档位推进/重置，写回模型参数后下次走 FSRS-lite。
                applyLegacy(record, mastered);
                return;
            }
            // 新入队：答错则冷启动初始化模型；答对不入队走旧清零语义。
            if (!weak) {
                record.setWrong(0);
                record.setNextReviewAt(null);
                return;
            }
            state = ReviewScheduler.coldStartAfterLapse();
        } else {
            state = mastered ? ReviewScheduler.onSuccess(state) : ReviewScheduler.onFailure(state);
        }
        int intervalDays = ReviewScheduler.intervalDays(state);
        LocalDateTime base = record.getLastAnswerAt() == null ? LocalDateTime.now() : record.getLastAnswerAt();
        boolean complete = mastered && ReviewScheduler.isComplete(state, intervalDays);
        writeModel(record, state, intervalDays);
        record.setWrong(complete ? 0 : 1);
        record.setNextReviewAt(complete ? null : base.plusDays(intervalDays));
    }

    /**
     * 旧固定档位逻辑（V4_140 原实现，仅存量未初始化记录 fallback 使用）。
     * 本次调度保持旧结果，同时用旧档位初始化模型状态，确保下一次复习进入 FSRS-lite。
     */
    private static void applyLegacy(UserQuestionRecord record, boolean mastered) {
        int stage = record.getReviewStage() == null ? 0
                : Math.max(0, Math.min(record.getReviewStage(), INTERVALS.length));
        stage = mastered ? Math.min(stage + 1, INTERVALS.length) : 0;
        record.setReviewStage(stage);
        record.setWrong(stage < INTERVALS.length ? 1 : 0);
        int days = INTERVALS[Math.min(stage, INTERVALS.length - 1)];
        record.setReviewIntervalDays(days);
        LocalDateTime base = record.getLastAnswerAt() == null ? LocalDateTime.now() : record.getLastAnswerAt();
        record.setNextReviewAt(stage < INTERVALS.length ? base.plusDays(days) : null);

        record.setMemoryStability((double) days);
        record.setMemoryDifficulty(ReviewScheduler.INITIAL_DIFFICULTY);
        record.setReviewReps(mastered ? Math.max(stage, 1) : 0);
        record.setReviewLapses(mastered ? 0 : 1);
    }

    /** 把 FSRS-lite 状态写回实体，并同步旧列（review_interval_days/review_stage 取最近档位映射）。 */
    private static void writeModel(UserQuestionRecord record, ReviewScheduler.MemoryState state, int intervalDays) {
        record.setMemoryStability(state.stability());
        record.setMemoryDifficulty(state.difficulty());
        record.setReviewReps(state.reps());
        record.setReviewLapses(state.lapses());
        int stageIndex = ReviewScheduler.legacyStageIndex(intervalDays);
        record.setReviewStage(stageIndex);
        record.setReviewIntervalDays(ReviewScheduler.legacyIntervalDays(stageIndex));
    }

    private static ReviewScheduler.MemoryState memoryStateOf(UserQuestionRecord record) {
        int reps = record.getReviewReps() == null ? 0 : record.getReviewReps();
        int lapses = record.getReviewLapses() == null ? 0 : record.getReviewLapses();
        return new ReviewScheduler.MemoryState(record.getMemoryStability(), record.getMemoryDifficulty(), reps, lapses);
    }
}
