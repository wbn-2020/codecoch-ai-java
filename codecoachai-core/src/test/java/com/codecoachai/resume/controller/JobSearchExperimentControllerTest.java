package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobSearchExperimentReviewSaveDTO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentMetricsVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentReviewVO;
import com.codecoachai.resume.service.JobSearchExperimentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSearchExperimentControllerTest {

    @Mock
    private JobSearchExperimentService jobSearchExperimentService;

    private JobSearchExperimentController controller;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .username("phase3-user")
                .build());
        controller = new JobSearchExperimentController(jobSearchExperimentService);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void reviewAliasUsesDesignReviewEndpointContract() {
        JobSearchExperimentReviewSaveDTO dto = new JobSearchExperimentReviewSaveDTO();
        JobSearchExperimentReviewVO expected = new JobSearchExperimentReviewVO();
        when(jobSearchExperimentService.createReview(7L, dto)).thenReturn(expected);

        JobSearchExperimentReviewVO actual = controller.createReviewAlias(7L, dto).getData();

        assertSame(expected, actual);
        verify(jobSearchExperimentService).createReview(7L, dto);
    }

    @Test
    void insightsAliasReturnsExperimentMetricsForDesignEndpointContract() {
        JobSearchExperimentMetricsVO expected = new JobSearchExperimentMetricsVO();
        when(jobSearchExperimentService.metrics(7L)).thenReturn(expected);

        JobSearchExperimentMetricsVO actual = controller.insights(7L).getData();

        assertSame(expected, actual);
        verify(jobSearchExperimentService).metrics(7L);
    }
}
