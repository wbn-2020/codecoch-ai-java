package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.interview.domain.dto.InterviewComparisonCreateDTO;
import com.codecoachai.interview.domain.dto.InterviewRemediationCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewComparisonVO;
import com.codecoachai.interview.domain.vo.InterviewRemediationOptionsVO;
import com.codecoachai.interview.domain.vo.InterviewRemediationVO;
import com.codecoachai.interview.service.InterviewComparisonService;
import com.codecoachai.interview.service.InterviewRemediationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewRemediationComparisonControllerTest {

    @Mock
    private InterviewRemediationService remediationService;
    @Mock
    private InterviewComparisonService comparisonService;

    @Test
    void remediationControllerDelegatesCreate() {
        InterviewRemediationCreateDTO dto = new InterviewRemediationCreateDTO();
        InterviewRemediationVO expected = new InterviewRemediationVO();
        expected.setTargetSessionId(200L);
        when(remediationService.create(dto)).thenReturn(expected);

        var result = new InterviewRemediationController(remediationService).create(dto).getData();

        assertEquals(200L, result.getTargetSessionId());
        verify(remediationService).create(dto);
    }

    @Test
    void comparisonControllerReturnsUnavailableReasons() {
        InterviewComparisonCreateDTO dto = new InterviewComparisonCreateDTO();
        dto.setReportIds(List.of(11L, 12L));
        InterviewComparisonVO expected = new InterviewComparisonVO();
        expected.setComparable(false);
        when(comparisonService.compare(dto)).thenReturn(expected);

        var result = new InterviewComparisonController(comparisonService).compare(dto).getData();

        assertEquals(false, result.getComparable());
        verify(comparisonService).compare(dto);
    }

    @Test
    void remediationControllerReturnsOptionsForInterview() {
        InterviewRemediationOptionsVO expected = new InterviewRemediationOptionsVO();
        expected.setInterviewId(100L);
        when(remediationService.options(100L)).thenReturn(expected);

        var result = new InterviewRemediationController(remediationService).options(100L).getData();

        assertEquals(100L, result.getInterviewId());
        verify(remediationService).options(100L);
    }

    @Test
    void comparisonControllerReturnsOwnedListAndDetail() {
        InterviewComparisonVO expected = new InterviewComparisonVO();
        expected.setId(900L);
        when(comparisonService.list(10)).thenReturn(List.of(expected));
        when(comparisonService.detail(900L)).thenReturn(expected);
        InterviewComparisonController controller = new InterviewComparisonController(comparisonService);

        assertEquals(900L, controller.list(10).getData().get(0).getId());
        assertEquals(900L, controller.detail(900L).getData().getId());
        verify(comparisonService).list(10);
        verify(comparisonService).detail(900L);
    }
}
