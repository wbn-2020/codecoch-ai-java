package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.service.InterviewStreamService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewControllerTest {

    @Mock
    private InterviewService interviewService;
    @Mock
    private InterviewStreamService interviewStreamService;

    @Test
    void listReturnsReportIdentityAndTotalScore() {
        InterviewListVO row = new InterviewListVO();
        row.setId(1L);
        row.setReportId(88L);
        row.setTotalScore(65);
        PageResult<InterviewListVO> expected = PageResult.of(List.of(row), 1L, 1L, 10L);
        when(interviewService.list(1L, 10L, null, "GENERATED", null)).thenReturn(expected);
        InterviewController controller = new InterviewController(interviewService, interviewStreamService);

        PageResult<InterviewListVO> result = controller.list(1L, 10L, null, "GENERATED", null).getData();

        assertEquals(88L, result.getRecords().get(0).getReportId());
        assertEquals(65, result.getRecords().get(0).getTotalScore());
        verify(interviewService).list(1L, 10L, null, "GENERATED", null);
    }
}
