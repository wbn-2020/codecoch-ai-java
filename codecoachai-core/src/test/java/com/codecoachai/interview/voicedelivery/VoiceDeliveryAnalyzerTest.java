package com.codecoachai.interview.voicedelivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceDeliveryAnalyzerTest {

    private final VoiceDeliveryAnalyzer analyzer = new VoiceDeliveryAnalyzer();

    @Test
    void doesNotInventPauseMetricsWithoutWordTimestamps() {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();
        dto.setTranscript("嗯 我会先确认需求 然后解释取舍");
        dto.setAudioDurationMs(12000L);
        dto.setTimestampSource("PROVIDER");

        VoiceDeliveryMetrics metrics = analyzer.analyze(dto,
                new ProviderExecutionContext("analysis-no-timestamps", Duration.ofSeconds(1)));

        assertFalse(metrics.isTimestampsAvailable());
        assertFalse(metrics.isPauseMetricsAvailable());
        assertNull(metrics.getPauseCount());
        assertNull(metrics.getAveragePauseMs());
        assertNull(metrics.getLongestPauseMs());
        assertTrue(metrics.getWarningCodes().contains("WORD_TIMESTAMPS_REQUIRED_FOR_PAUSE_METRICS"));
    }

    @Test
    void computesPausesOnlyFromSuppliedOrderedTimestamps() {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();
        dto.setTranscript("first second third");
        dto.setAudioDurationMs(3000L);
        dto.setWordTimings(List.of(
                timing("first", 0, 300),
                timing("second", 900, 1200),
                timing("third", 2200, 2500)));

        VoiceDeliveryMetrics metrics = analyzer.analyze(dto,
                new ProviderExecutionContext("analysis-with-timestamps", Duration.ofSeconds(1)));

        assertTrue(metrics.isPauseMetricsAvailable());
        assertEquals(2, metrics.getPauseCount());
        assertEquals(800L, metrics.getAveragePauseMs());
        assertEquals(1000L, metrics.getLongestPauseMs());
    }

    @Test
    void rejectsWordTimingBeyondAudioDuration() {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();
        dto.setTranscript("one");
        dto.setAudioDurationMs(1000L);
        dto.setWordTimings(List.of(timing("one", 100, 1001)));

        assertThrows(BusinessException.class, () -> analyzer.analyze(dto,
                new ProviderExecutionContext("analysis-timing-out-of-range", Duration.ofSeconds(1))));
    }

    @Test
    void derivesWordCountFromEvidenceBackedTranscriptInsteadOfClientTimingCount() {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();
        dto.setTranscript("trusted words");
        dto.setAudioDurationMs(3000L);
        dto.setWordTimings(List.of(
                timing("untrusted", 0, 200),
                timing("timing", 300, 500),
                timing("count", 600, 800)));

        VoiceDeliveryMetrics metrics = analyzer.analyze(dto,
                new ProviderExecutionContext("analysis-evidence-word-count", Duration.ofSeconds(1)));

        assertEquals(2, metrics.getWordCount());
        assertEquals(new java.math.BigDecimal("40.00"), metrics.getSpeakingRatePerMinute());
    }

    private VoiceWordTimingDTO timing(String text, long start, long end) {
        VoiceWordTimingDTO timing = new VoiceWordTimingDTO();
        timing.setText(text);
        timing.setStartMs(start);
        timing.setEndMs(end);
        return timing;
    }
}
