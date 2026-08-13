package com.codecoachai.resume.careerreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerCareerCampaignReviewEvidenceControllerTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 7, 26, 0, 0);

    @Mock
    private CareerCampaignReviewEvidenceService evidenceService;

    private InnerCareerCampaignReviewEvidenceController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerCareerCampaignReviewEvidenceController(evidenceService);
    }

    @Test
    void aiServiceCallerPassesThrough() {
        CareerCampaignReviewEvidenceVO vo = new CareerCampaignReviewEvidenceVO();
        when(evidenceService.get(10L, 20L, CUTOFF, null, null)).thenReturn(vo);

        var result = controller.get("codecoachai-ai", 10L, 20L, CUTOFF, null, null);

        assertEquals(vo, result.getData());
        verify(evidenceService).get(10L, 20L, CUTOFF, null, null);
    }

    @Test
    void otherTrustedServiceIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.get("codecoachai-question", 10L, 20L, CUTOFF, null, null));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verifyNoInteractions(evidenceService);
    }

    @Test
    void missingServiceNameHeaderIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.get(null, 10L, 20L, CUTOFF, null, null));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verifyNoInteractions(evidenceService);
    }

    @Test
    void serviceNameMatchIsExactNotPrefix() {
        assertThrows(BusinessException.class,
                () -> controller.get("codecoachai-ai-fake", 10L, 20L, CUTOFF, null, null));
        assertThrows(BusinessException.class,
                () -> controller.get("CODECOACHAI-AI", 10L, 20L, CUTOFF, null, null));
        verifyNoInteractions(evidenceService);
    }
}
