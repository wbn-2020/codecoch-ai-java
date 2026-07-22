package com.codecoachai.ai.agent.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class InnerCampaignArchiveSourceMapperContractTest {

    @Test
    void archiveQueriesSelectHistoricalSnapshotsAtOrBeforeCutoff() throws Exception {
        String reviewSql = sql(InnerCampaignArchiveSourceMapper.class.getMethod(
                "selectReview", Long.class, Long.class, LocalDateTime.class));
        String pulseSql = sql(InnerCampaignArchiveSourceMapper.class.getMethod(
                "selectPulses", Long.class, Long.class, LocalDateTime.class));

        assertTrue(reviewSql.contains("snapshot.review_id = review.id"), reviewSql);
        assertFalse(reviewSql.contains("review.current_snapshot_id"), reviewSql);
        assertTrue(reviewSql.contains("snapshot.data_cutoff_at <= #{datacutoffat}"), reviewSql);

        assertTrue(pulseSql.contains("pulse.id = snapshot.pulse_id"), pulseSql);
        assertFalse(pulseSql.contains("pulse.current_snapshot_id"), pulseSql);
        assertTrue(pulseSql.contains("snapshot.data_cutoff_at <= #{datacutoffat}"), pulseSql);
    }

    private String sql(Method method) {
        return String.join("\n", method.getAnnotation(Select.class).value())
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
