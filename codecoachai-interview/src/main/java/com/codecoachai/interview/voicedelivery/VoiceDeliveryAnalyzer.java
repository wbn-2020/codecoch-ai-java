package com.codecoachai.interview.voicedelivery;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class VoiceDeliveryAnalyzer {

    private static final long PAUSE_THRESHOLD_MS = 500L;
    private static final List<String> FILLERS = List.of("嗯", "呃", "额", "然后", "就是", "那个", "um", "uh");
    private static final Pattern LATIN_TOKEN = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern CJK_CHARACTER = Pattern.compile("[\\p{IsHan}]");

    public VoiceDeliveryMetrics analyze(VoiceDeliveryAnalysisCreateDTO dto, ProviderExecutionContext context) {
        context.checkActive();
        List<VoiceWordTimingDTO> timings = normalizedTimings(dto.getWordTimings());
        int wordCount = countTranscriptUnits(dto.getTranscript());
        BigDecimal speakingRate = dto.getAudioDurationMs() == null || dto.getAudioDurationMs() <= 0
                ? null
                : BigDecimal.valueOf(wordCount * 60000.0 / dto.getAudioDurationMs())
                        .setScale(2, RoundingMode.HALF_UP);
        int fillerCount = countFillers(dto.getTranscript());
        context.checkActive();

        if (timings.isEmpty()) {
            return VoiceDeliveryMetrics.builder()
                    .wordCount(wordCount)
                    .speakingRatePerMinute(speakingRate)
                    .fillerCount(fillerCount)
                    .timestampsAvailable(false)
                    .pauseMetricsAvailable(false)
                    .warningCodes(List.of("WORD_TIMESTAMPS_REQUIRED_FOR_PAUSE_METRICS"))
                    .build();
        }

        List<Long> pauses = new ArrayList<>();
        long previousEnd = -1L;
        for (VoiceWordTimingDTO timing : timings) {
            context.checkActive();
            Long startMs = timing.getStartMs();
            Long endMs = timing.getEndMs();
            if (startMs == null || endMs == null || startMs < 0 || endMs < startMs || startMs < previousEnd) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Word timings must be ordered and non-overlapping");
            }
            if (dto.getAudioDurationMs() == null || endMs > dto.getAudioDurationMs()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Word timings cannot exceed audio duration");
            }
            if (previousEnd >= 0) {
                long gap = startMs - previousEnd;
                if (gap >= PAUSE_THRESHOLD_MS) {
                    pauses.add(gap);
                }
            }
            previousEnd = endMs;
        }
        long totalPause = pauses.stream().mapToLong(Long::longValue).sum();
        return VoiceDeliveryMetrics.builder()
                .wordCount(wordCount)
                .speakingRatePerMinute(speakingRate)
                .fillerCount(fillerCount)
                .timestampsAvailable(true)
                .pauseMetricsAvailable(true)
                .pauseCount(pauses.size())
                .averagePauseMs(pauses.isEmpty() ? 0L : totalPause / pauses.size())
                .longestPauseMs(pauses.stream().mapToLong(Long::longValue).max().orElse(0L))
                .warningCodes(List.of())
                .build();
    }

    private List<VoiceWordTimingDTO> normalizedTimings(List<VoiceWordTimingDTO> timings) {
        return timings == null ? List.of() : List.copyOf(timings);
    }

    private int countTranscriptUnits(String transcript) {
        Matcher latin = LATIN_TOKEN.matcher(transcript);
        int latinTokens = 0;
        while (latin.find()) {
            latinTokens++;
        }
        Matcher cjk = CJK_CHARACTER.matcher(transcript);
        int cjkCharacters = 0;
        while (cjk.find()) {
            cjkCharacters++;
        }
        return Math.max(1, latinTokens + cjkCharacters);
    }

    private int countFillers(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String filler : FILLERS) {
            int offset = 0;
            while ((offset = lower.indexOf(filler, offset)) >= 0) {
                count++;
                offset += filler.length();
            }
        }
        return count;
    }
}
