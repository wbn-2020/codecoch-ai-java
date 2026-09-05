package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO;
import java.time.LocalDateTime;
import java.util.List;

public interface WeeklyCareerEvidenceService {

    WeeklyCareerEvidenceVO getWeeklyEvidence(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId,
            String timezone,
            List<Long> experimentIds);
}
