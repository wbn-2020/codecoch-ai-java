package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
import com.codecoachai.interview.domain.vo.StudyPlanDailyViewVO;
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.service.StudyPlanService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyPlanControllerTest {

    @Mock
    private StudyPlanService studyPlanService;

    @Test
    void dailyTasksSelectsLatestActivePlanByDefault() {
        StudyPlanListVO activePlan = new StudyPlanListVO();
        activePlan.setId(22L);
        activePlan.setPlanStatus("ACTIVE");
        when(studyPlanService.list(any())).thenReturn(PageResult.of(List.of(activePlan), 2L, 1L, 1L));
        StudyPlanDailyViewVO dailyView = new StudyPlanDailyViewVO();
        dailyView.setPlanId(22L);
        when(studyPlanService.dailyView(22L, null)).thenReturn(dailyView);
        StudyPlanController controller = new StudyPlanController(studyPlanService);

        StudyPlanDailyViewVO result = controller.dailyTasks(null, null).getData();

        assertEquals(22L, result.getPlanId());
        ArgumentCaptor<StudyPlanQueryDTO> queryCaptor = ArgumentCaptor.forClass(StudyPlanQueryDTO.class);
        verify(studyPlanService).list(queryCaptor.capture());
        assertEquals("ACTIVE", queryCaptor.getValue().getPlanStatus());
        assertEquals(1L, queryCaptor.getValue().getPageNo());
        assertEquals(1L, queryCaptor.getValue().getPageSize());
    }
}
