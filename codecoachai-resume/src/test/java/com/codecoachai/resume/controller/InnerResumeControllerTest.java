package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordAgentEvidenceVO;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.service.ResumeService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerResumeControllerTest {

    @Mock
    private ResumeService resumeService;
    @Mock
    private ResumeMapper resumeMapper;

    private InnerResumeController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerResumeController(resumeService, resumeMapper);
    }

    @Test
    void getOptimizeRecordEvidenceReturnsOwnedSuccessfulRecordEvidence() {
        ResumeOptimizeRecordAgentEvidenceVO expected = evidence();
        when(resumeService.getOptimizeRecordEvidence(10L, 7001L)).thenReturn(expected);

        ResumeOptimizeRecordAgentEvidenceVO evidence =
                controller.getOptimizeRecordEvidence(10L, 7001L).getData();

        assertEquals(7001L, evidence.getId());
        assertEquals(10L, evidence.getUserId());
        assertEquals(100L, evidence.getResumeId());
        assertEquals(501L, evidence.getTargetJobId());
        assertEquals(ResumeOptimizeStatus.SUCCESS.getCode(), evidence.getStatus());
        verify(resumeService).getOptimizeRecordEvidence(10L, 7001L);
    }

    @Test
    void getOptimizeRecordEvidenceRejectsFailedRecordEvidence() {
        when(resumeService.getOptimizeRecordEvidence(10L, 7002L)).thenThrow(BusinessException.class);

        assertThrows(BusinessException.class, () -> controller.getOptimizeRecordEvidence(10L, 7002L));
    }

    private ResumeOptimizeRecordAgentEvidenceVO evidence() {
        ResumeOptimizeRecordAgentEvidenceVO evidence = new ResumeOptimizeRecordAgentEvidenceVO();
        evidence.setId(7001L);
        evidence.setUserId(10L);
        evidence.setResumeId(100L);
        evidence.setTargetJobId(501L);
        evidence.setStatus(ResumeOptimizeStatus.SUCCESS.getCode());
        evidence.setCreatedAt(LocalDateTime.of(2026, 6, 18, 9, 0));
        evidence.setOptimizedAt(LocalDateTime.of(2026, 6, 18, 9, 10));
        return evidence;
    }
}
