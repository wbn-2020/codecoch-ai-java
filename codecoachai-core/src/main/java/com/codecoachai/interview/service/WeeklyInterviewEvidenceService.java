package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO;
import java.time.LocalDateTime;

public interface WeeklyInterviewEvidenceService {

    WeeklyInterviewEvidenceVO getWeeklyEvidence(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId,
            String timezone);
}
