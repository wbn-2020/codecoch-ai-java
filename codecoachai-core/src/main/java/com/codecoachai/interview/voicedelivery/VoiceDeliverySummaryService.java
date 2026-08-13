package com.codecoachai.interview.voicedelivery;

import java.util.List;
import java.util.Map;

public interface VoiceDeliverySummaryService {

    VoiceDeliverySummaryVO summary(Long userId, Long sessionId);

    Map<Long, VoiceDeliverySummaryVO> summaries(Long userId, List<Long> sessionIds);
}
