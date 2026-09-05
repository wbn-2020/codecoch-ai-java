package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO;
import com.codecoachai.resume.service.WeeklyCareerEvidenceService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerWeeklyCareerEvidenceControllerTest {

    @Mock
    private WeeklyCareerEvidenceService service;

    private InnerWeeklyCareerEvidenceController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerWeeklyCareerEvidenceController(service);
    }

    @Test
    void delegatesTheFeignContractWithoutChangingScopeOrUtcBounds() {
        Long userId = 10L;
        LocalDateTime rangeStart = LocalDateTime.of(2026, 7, 12, 16, 0);
        LocalDateTime rangeEnd = LocalDateTime.of(2026, 7, 19, 16, 0);
        LocalDateTime sourceCutoffAt = LocalDateTime.of(2026, 7, 18, 12, 0);
        Long targetJobId = 20L;
        List<Long> experimentIds = List.of(30L, 31L);
        WeeklyCareerEvidenceVO evidence = new WeeklyCareerEvidenceVO();
        evidence.setUserId(userId);
        when(service.getWeeklyEvidence(
                userId,
                rangeStart,
                rangeEnd,
                sourceCutoffAt,
                targetJobId,
                "Asia/Shanghai",
                experimentIds))
                .thenReturn(evidence);

        WeeklyCareerEvidenceVO result = controller.getWeeklyEvidence(
                userId,
                rangeStart,
                rangeEnd,
                sourceCutoffAt,
                targetJobId,
                "Asia/Shanghai",
                experimentIds).getData();

        assertEquals(userId, result.getUserId());
        verify(service).getWeeklyEvidence(
                userId,
                rangeStart,
                rangeEnd,
                sourceCutoffAt,
                targetJobId,
                "Asia/Shanghai",
                experimentIds);
    }
}
