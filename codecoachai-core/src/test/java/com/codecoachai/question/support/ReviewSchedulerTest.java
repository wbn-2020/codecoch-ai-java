package com.codecoachai.question.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewSchedulerTest {

    @Test
    void consecutiveSuccessesIncreaseIntervalsWithinBounds() {
        ReviewScheduler.MemoryState state = ReviewScheduler.coldStartAfterLapse();
        int previousInterval = ReviewScheduler.intervalDays(state);

        for (int i = 0; i < 12; i++) {
            state = ReviewScheduler.onSuccess(state);
            int interval = ReviewScheduler.intervalDays(state);
            assertTrue(interval >= previousInterval);
            assertTrue(interval >= ReviewScheduler.MIN_INTERVAL_DAYS);
            assertTrue(interval <= ReviewScheduler.MAX_INTERVAL_DAYS);
            previousInterval = interval;
        }

        assertEquals(ReviewScheduler.MAX_INTERVAL_DAYS, previousInterval);
    }

    @Test
    void failureReducesStabilityAndRaisesDifficultyAndLapses() {
        ReviewScheduler.MemoryState previous = new ReviewScheduler.MemoryState(20.0, 6.0, 4, 1);

        ReviewScheduler.MemoryState failed = ReviewScheduler.onFailure(previous);

        assertEquals(2.0, failed.stability());
        assertEquals(7.0, failed.difficulty());
        assertEquals(4, failed.reps());
        assertEquals(2, failed.lapses());
        assertTrue(ReviewScheduler.intervalDays(failed) <= 2);
    }

    @Test
    void difficultQuestionsGrowMoreSlowly() {
        ReviewScheduler.MemoryState easy = new ReviewScheduler.MemoryState(7.0, 2.0, 2, 0);
        ReviewScheduler.MemoryState hard = new ReviewScheduler.MemoryState(7.0, 9.0, 2, 0);

        ReviewScheduler.MemoryState easyAfterSuccess = ReviewScheduler.onSuccess(easy);
        ReviewScheduler.MemoryState hardAfterSuccess = ReviewScheduler.onSuccess(hard);

        assertTrue(easyAfterSuccess.stability() > hardAfterSuccess.stability());
        assertTrue(ReviewScheduler.intervalDays(easyAfterSuccess)
                > ReviewScheduler.intervalDays(hardAfterSuccess));
    }

    @Test
    void coldStartAndUpperBoundAreStable() {
        assertEquals(1, ReviewScheduler.intervalDays(null));
        assertEquals(1, ReviewScheduler.intervalDays(new ReviewScheduler.MemoryState(null, null, 0, 0)));
        assertEquals(1, ReviewScheduler.intervalDays(ReviewScheduler.coldStartAfterLapse()));
        assertEquals(60, ReviewScheduler.intervalDays(
                new ReviewScheduler.MemoryState(10_000.0, 1.0, 20, 0)));
    }

    @Test
    void legacyStageMappingUsesNearestConservativeBucket() {
        assertEquals(0, ReviewScheduler.legacyStageIndex(1));
        assertEquals(0, ReviewScheduler.legacyStageIndex(2));
        assertEquals(1, ReviewScheduler.legacyStageIndex(3));
        assertEquals(1, ReviewScheduler.legacyStageIndex(5));
        assertEquals(2, ReviewScheduler.legacyStageIndex(7));
        assertEquals(2, ReviewScheduler.legacyStageIndex(11));
        assertEquals(3, ReviewScheduler.legacyStageIndex(15));
        assertEquals(3, ReviewScheduler.legacyStageIndex(60));

        assertEquals(1, ReviewScheduler.legacyIntervalDays(-1));
        assertEquals(1, ReviewScheduler.legacyIntervalDays(0));
        assertEquals(3, ReviewScheduler.legacyIntervalDays(1));
        assertEquals(7, ReviewScheduler.legacyIntervalDays(2));
        assertEquals(15, ReviewScheduler.legacyIntervalDays(3));
        assertEquals(1, ReviewScheduler.legacyIntervalDays(4));
    }
}
