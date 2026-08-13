package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.vo.StudyPlanAgentEvidenceVO;
import com.codecoachai.interview.service.StudyPlanService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerStudyPlanControllerTest {

    @Mock
    private StudyPlanService studyPlanService;

    private InnerStudyPlanController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerStudyPlanController(studyPlanService);
    }

    @Test
    void getPlanEvidenceReturnsOwnedActivePlanEvidence() {
        StudyPlanAgentEvidenceVO expected = evidence();
        when(studyPlanService.getPlanEvidence(10L, 8001L)).thenReturn(expected);

        StudyPlanAgentEvidenceVO evidence = controller.getPlanEvidence(10L, 8001L).getData();

        assertEquals(8001L, evidence.getId());
        assertEquals(10L, evidence.getUserId());
        assertEquals(501L, evidence.getTargetJobId());
        assertEquals("ACTIVE", evidence.getPlanStatus());
        verify(studyPlanService).getPlanEvidence(10L, 8001L);
    }

    @Test
    void getPlanEvidenceRejectsInactivePlanEvidence() {
        when(studyPlanService.getPlanEvidence(10L, 8002L)).thenThrow(BusinessException.class);

        assertThrows(BusinessException.class, () -> controller.getPlanEvidence(10L, 8002L));
    }

    private StudyPlanAgentEvidenceVO evidence() {
        StudyPlanAgentEvidenceVO evidence = new StudyPlanAgentEvidenceVO();
        evidence.setId(8001L);
        evidence.setUserId(10L);
        evidence.setTargetJobId(501L);
        evidence.setPlanStatus("ACTIVE");
        evidence.setCreatedAt(LocalDateTime.of(2026, 6, 18, 10, 0));
        evidence.setGeneratedAt(LocalDateTime.of(2026, 6, 18, 10, 20));
        return evidence;
    }
}
