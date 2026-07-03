package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.domain.vo.ApplicationCareerInsightSummaryVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.service.V4ResumeCareerService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerJobApplicationControllerTest {

    @Mock
    private V4ResumeCareerService v4ResumeCareerService;
    @Mock
    private JobApplicationEventMapper jobApplicationEventMapper;

    private InnerJobApplicationController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerJobApplicationController(v4ResumeCareerService, jobApplicationEventMapper);
    }

    @Test
    void getCareerInsightSummaryDelegatesToServiceWithRequestedDays() {
        ApplicationCareerInsightSummaryVO expected = new ApplicationCareerInsightSummaryVO();
        expected.setRangeDays(30);
        when(v4ResumeCareerService.getApplicationCareerInsightSummaryForUser(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(30),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(expected);

        ApplicationCareerInsightSummaryVO result =
                controller.getCareerInsightSummary(10L, 30).getData();

        assertEquals(30, result.getRangeDays());
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(v4ResumeCareerService).getApplicationCareerInsightSummaryForUser(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(30),
                nowCaptor.capture());
    }
}
