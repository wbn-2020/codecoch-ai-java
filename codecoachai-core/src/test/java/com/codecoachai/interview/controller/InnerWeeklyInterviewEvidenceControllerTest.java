package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO;
import com.codecoachai.interview.service.WeeklyInterviewEvidenceService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerWeeklyInterviewEvidenceControllerTest {

    @Mock
    private WeeklyInterviewEvidenceService service;

    private InnerWeeklyInterviewEvidenceController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerWeeklyInterviewEvidenceController(service);
    }

    @Test
    void delegatesTheFeignContractWithoutChangingUtcBoundsOrScope() {
        Long userId = 10L;
        LocalDateTime rangeStartUtc = LocalDateTime.of(2026, 7, 12, 16, 0);
        LocalDateTime rangeEndUtc = LocalDateTime.of(2026, 7, 19, 16, 0);
        LocalDateTime sourceCutoffAt = LocalDateTime.of(2026, 7, 18, 12, 0);
        Long targetJobId = 20L;
        WeeklyInterviewEvidenceVO evidence = new WeeklyInterviewEvidenceVO();
        evidence.setUserId(userId);
        when(service.getWeeklyEvidence(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                "Asia/Shanghai"))
                .thenReturn(evidence);

        WeeklyInterviewEvidenceVO result = controller.getWeeklyEvidence(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                "Asia/Shanghai").getData();

        assertEquals(userId, result.getUserId());
        verify(service).getWeeklyEvidence(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                "Asia/Shanghai");
    }
}
