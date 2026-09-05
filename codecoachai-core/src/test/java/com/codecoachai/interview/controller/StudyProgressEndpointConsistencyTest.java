package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codecoachai.common.mybatis.statistics.StudyProgressSnapshot;
import com.codecoachai.common.mybatis.statistics.StudyProgressStatisticsService;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.mapper.StudyCheckinMapper;
import com.codecoachai.interview.mapper.StudyPlanMapper;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import com.codecoachai.user.controller.V3DashboardController;
import com.codecoachai.user.domain.vo.V3DashboardVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StudyProgressEndpointConsistencyTest {

    @AfterEach
    void clearLoginUser() {
        LoginUserContext.clear();
    }

    @Test
    void studyAndDashboardEndpointsExposeTheSameAuthoritativeSnapshot() throws Exception {
        Long userId = 1001L;
        StudyProgressSnapshot snapshot = snapshot();
        StudyProgressStatisticsService statisticsService = mock(StudyProgressStatisticsService.class);
        when(statisticsService.current(userId)).thenReturn(snapshot);
        when(statisticsService.forPlan(userId, null)).thenReturn(snapshot);
        when(statisticsService.current(userId, 77L)).thenReturn(snapshot);
        LoginUserContext.setLoginUser(new LoginUser(userId, "user", "User", List.of("USER")));

        StudyCheckinController checkinController = new StudyCheckinController(
                mock(StudyCheckinMapper.class),
                mock(StudyTaskMapper.class),
                mock(StudyPlanMapper.class),
                statisticsService);
        StudyCheckinController.StreakVO streak = checkinController.streak().getData();
        StudyCheckinController.ProgressVO progress = checkinController.progress(null).getData();

        V3DashboardController dashboardController = new V3DashboardController(
                null,
                new ObjectMapper(),
                statisticsService);
        Method method = V3DashboardController.class.getDeclaredMethod("studyProgress", Long.class, Long.class);
        method.setAccessible(true);
        V3DashboardVO.StudyProgressVO dashboard =
                (V3DashboardVO.StudyProgressVO) method.invoke(dashboardController, userId, 77L);

        assertEquals(snapshot.getCurrentStreak(), streak.getCurrentStreak());
        assertEquals(snapshot.getTotalCheckinDays(), streak.getTotalCheckinDays());
        assertEquals(snapshot.isCheckedInToday(), streak.isCheckedInToday());
        assertEquals(snapshot.getBusinessDate(), streak.getBusinessDate());
        assertEquals(snapshot.getBusinessTimezone(), streak.getBusinessTimezone());

        assertEquals(snapshot.getPlanId(), progress.getPlanId());
        assertEquals(snapshot.getTotalTasks(), progress.getTotalTasks());
        assertEquals(snapshot.getCompletedTasks(), progress.getCompletedTasks());
        assertEquals(snapshot.getSkippedTasks(), progress.getSkippedTasks());
        assertEquals(snapshot.getPendingTasks(), progress.getPendingTasks());
        assertEquals(snapshot.getCompletionRate(), progress.getCompletionRate());

        assertEquals(progress.getPlanId(), dashboard.getActivePlanId());
        assertEquals((long) progress.getTotalTasks(), dashboard.getTotalTasks());
        assertEquals((long) progress.getCompletedTasks(), dashboard.getCompletedTasks());
        assertEquals((long) progress.getSkippedTasks(), dashboard.getSkippedTasks());
        assertEquals((long) progress.getPendingTasks(), dashboard.getPendingTasks());
        assertEquals(progress.getCompletionRate(), dashboard.getCompletionRate());
        assertEquals(streak.getCurrentStreak(), dashboard.getCurrentStreak());
        assertEquals(streak.getTotalCheckinDays(), dashboard.getTotalCheckinDays());
        assertEquals(streak.isCheckedInToday(), dashboard.getCheckedInToday());
        assertEquals(streak.getBusinessDate(), dashboard.getBusinessDate());
        assertEquals(streak.getBusinessTimezone(), dashboard.getBusinessTimezone());
    }

    private StudyProgressSnapshot snapshot() {
        StudyProgressSnapshot snapshot = new StudyProgressSnapshot();
        snapshot.setPlanId(88L);
        snapshot.setTargetJobId(77L);
        snapshot.setTotalTasks(7);
        snapshot.setCompletedTasks(4);
        snapshot.setSkippedTasks(1);
        snapshot.setPendingTasks(2);
        snapshot.setCompletionRate(57);
        snapshot.setCurrentStreak(3);
        snapshot.setTotalCheckinDays(12);
        snapshot.setCheckedInToday(true);
        snapshot.setBusinessDate(LocalDate.of(2026, 8, 18));
        snapshot.setBusinessTimezone("Asia/Shanghai");
        return snapshot;
    }
}
