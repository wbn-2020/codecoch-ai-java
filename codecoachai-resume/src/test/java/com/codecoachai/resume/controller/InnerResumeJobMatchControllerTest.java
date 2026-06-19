package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportAgentEvidenceVO;
import com.codecoachai.resume.service.ResumeJobMatchService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerResumeJobMatchControllerTest {

    @Mock
    private ResumeJobMatchService resumeJobMatchService;

    private InnerResumeJobMatchController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerResumeJobMatchController(resumeJobMatchService);
    }

    @Test
    void getReportEvidenceReturnsOwnedSuccessfulReportEvidence() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 6, 18, 10, 30);
        ResumeJobMatchReportAgentEvidenceVO expected = evidence(9001L, 10L, ResumeJobMatchStatus.SUCCESS.getCode());
        expected.setGeneratedAt(generatedAt);
        when(resumeJobMatchService.getReportEvidence(10L, 9001L)).thenReturn(expected);

        ResumeJobMatchReportAgentEvidenceVO evidence =
                controller.getReportEvidence(10L, 9001L).getData();

        assertEquals(9001L, evidence.getId());
        assertEquals(10L, evidence.getUserId());
        assertEquals(100L, evidence.getResumeId());
        assertEquals(200L, evidence.getTargetJobId());
        assertEquals(300L, evidence.getResumeVersionId());
        assertEquals(ResumeJobMatchStatus.SUCCESS.getCode(), evidence.getStatus());
        assertEquals(generatedAt, evidence.getGeneratedAt());
        verify(resumeJobMatchService).getReportEvidence(10L, 9001L);
    }

    @Test
    void getReportEvidenceRejectsFailedReportEvidence() {
        when(resumeJobMatchService.getReportEvidence(10L, 9002L)).thenThrow(BusinessException.class);

        assertThrows(BusinessException.class, () -> controller.getReportEvidence(10L, 9002L));
    }

    private ResumeJobMatchReportAgentEvidenceVO evidence(Long id, Long userId, String status) {
        ResumeJobMatchReportAgentEvidenceVO evidence = new ResumeJobMatchReportAgentEvidenceVO();
        evidence.setId(id);
        evidence.setUserId(userId);
        evidence.setResumeId(100L);
        evidence.setTargetJobId(200L);
        evidence.setResumeVersionId(300L);
        evidence.setStatus(status);
        evidence.setCreatedAt(LocalDateTime.of(2026, 6, 18, 10, 0));
        evidence.setGeneratedAt(LocalDateTime.of(2026, 6, 18, 10, 20));
        return evidence;
    }
}
