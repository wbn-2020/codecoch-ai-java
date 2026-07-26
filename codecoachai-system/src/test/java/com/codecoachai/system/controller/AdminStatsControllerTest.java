package com.codecoachai.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.system.controller.AdminStatsController.DailyActivityVO;
import com.codecoachai.system.mapper.LoginLogMapper;
import com.codecoachai.system.mapper.LoginLogMapper.DailyActivityCount;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminStatsControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 26);

    @Mock
    private LoginLogMapper loginLogMapper;
    @Mock
    private AdminPermissionGuard adminPermissionGuard;

    private AdminStatsController controller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), ZoneOffset.UTC);
        controller = new AdminStatsController(loginLogMapper, adminPermissionGuard, clock);
    }

    @Test
    void activeUserTrendUsesOneGroupedQueryAndFillsMissingDates() {
        when(loginLogMapper.selectDailyActiveUserCounts(
                LocalDateTime.of(2026, 7, 24, 0, 0),
                LocalDateTime.of(2026, 7, 27, 0, 0)))
                .thenReturn(List.of(count(TODAY.minusDays(2), 2L), count(TODAY, 3L)));

        Result<List<DailyActivityVO>> response = controller.userActivityTrend(3);

        assertEquals(List.of("2026-07-24", "2026-07-25", "2026-07-26"),
                response.getData().stream().map(DailyActivityVO::getDate).toList());
        assertEquals(List.of(2, 0, 3),
                response.getData().stream().map(DailyActivityVO::getActiveUsers).toList());
        verify(loginLogMapper).selectDailyActiveUserCounts(
                LocalDateTime.of(2026, 7, 24, 0, 0),
                LocalDateTime.of(2026, 7, 27, 0, 0));
        verify(loginLogMapper, never()).selectCount(org.mockito.ArgumentMatchers.any());
        verify(adminPermissionGuard).require("admin:stats:list");
    }

    @Test
    void registrationTrendUsesOneGroupedQuery() {
        when(loginLogMapper.selectDailyRegistrationCounts(
                LocalDateTime.of(2026, 7, 25, 0, 0),
                LocalDateTime.of(2026, 7, 27, 0, 0)))
                .thenReturn(List.of(count(TODAY.minusDays(1), 4L)));

        Result<List<DailyActivityVO>> response = controller.newUserTrend(2);

        assertEquals(List.of(4, 0),
                response.getData().stream().map(DailyActivityVO::getActiveUsers).toList());
        verify(loginLogMapper).selectDailyRegistrationCounts(
                LocalDateTime.of(2026, 7, 25, 0, 0),
                LocalDateTime.of(2026, 7, 27, 0, 0));
        verify(loginLogMapper, never()).selectCount(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activeUserSqlCountsDistinctUsersAndGroupsByDate() throws Exception {
        Method method = LoginLogMapper.class.getMethod(
                "selectDailyActiveUserCounts", LocalDateTime.class, LocalDateTime.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .toUpperCase();

        assertTrue(sql.contains("COUNT(DISTINCT USER_ID)"));
        assertTrue(sql.contains("GROUP BY DATE(LOGIN_TIME)"));
    }

    private DailyActivityCount count(LocalDate date, Long value) {
        DailyActivityCount count = new DailyActivityCount();
        count.setActivityDate(date);
        count.setActivityCount(value);
        return count;
    }
}
