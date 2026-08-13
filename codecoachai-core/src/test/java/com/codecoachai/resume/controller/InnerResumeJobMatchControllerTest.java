package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportDetailVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
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
    void getSuccessReportReturnsInnerSuccessReport() {
        ResumeJobMatchReportDetailVO expected = new ResumeJobMatchReportDetailVO();
        expected.setReportId(9001L);
        expected.setUserId(10L);
        expected.setResumeId(100L);
        expected.setTargetJobId(200L);
        expected.setResumeVersionId(300L);
        expected.setJdAnalysisId(400L);
        expected.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        when(resumeJobMatchService.getInnerSuccessReport(9001L)).thenReturn(expected);

        ResumeJobMatchReportDetailVO report = controller.getSuccessReport(9001L).getData();

        assertEquals(9001L, report.getReportId());
        assertEquals(10L, report.getUserId());
        assertEquals(100L, report.getResumeId());
        assertEquals(200L, report.getTargetJobId());
        assertEquals(300L, report.getResumeVersionId());
        assertEquals(400L, report.getJdAnalysisId());
        assertEquals(ResumeJobMatchStatus.SUCCESS.getCode(), report.getStatus());
        verify(resumeJobMatchService).getInnerSuccessReport(9001L);
    }

    @Test
    void executeReportReturnsSubmitResult() {
        ResumeJobMatchSubmitVO expected = new ResumeJobMatchSubmitVO();
        expected.setReportId(9001L);
        expected.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        when(resumeJobMatchService.executeReport(9001L)).thenReturn(expected);

        ResumeJobMatchSubmitVO result = controller.executeReport(9001L).getData();

        assertEquals(9001L, result.getReportId());
        assertEquals(ResumeJobMatchStatus.SUCCESS.getCode(), result.getStatus());
        verify(resumeJobMatchService).executeReport(9001L);
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
