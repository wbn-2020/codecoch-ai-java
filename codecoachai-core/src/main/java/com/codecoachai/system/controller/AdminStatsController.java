package com.codecoachai.system.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.system.mapper.LoginLogMapper;
import com.codecoachai.system.mapper.LoginLogMapper.DailyActivityCount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端统计数据 Controller。
 * 提供用户活跃趋势、热门题目排行等数据。
 */
@Tag(name = "管理端统计数据")
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private static final String PERM_STATS_LIST = "admin:stats:list";
    private static final int MAX_DAYS = 180;

    private final LoginLogMapper loginLogMapper;
    private final AdminPermissionGuard adminPermissionGuard;
    private final Clock clock;

    @Autowired
    public AdminStatsController(LoginLogMapper loginLogMapper, AdminPermissionGuard adminPermissionGuard) {
        this(loginLogMapper, adminPermissionGuard, Clock.systemDefaultZone());
    }

    AdminStatsController(LoginLogMapper loginLogMapper, AdminPermissionGuard adminPermissionGuard, Clock clock) {
        this.loginLogMapper = loginLogMapper;
        this.adminPermissionGuard = adminPermissionGuard;
        this.clock = clock;
    }

    @Operation(summary = "用户活跃趋势（最近N天每日登录人数）")
    @OperationLog(module = "system", action = "QUERY_USER_ACTIVITY_TREND", description = "查询用户活跃趋势", logArgs = false)
    @GetMapping("/user-activity-trend")
    public Result<List<DailyActivityVO>> userActivityTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        adminPermissionGuard.require(PERM_STATS_LIST);
        days = normalizeDays(days);
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusDays(days - 1L);
        List<DailyActivityCount> counts = loginLogMapper.selectDailyActiveUserCounts(
                startDate.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return Result.success(fillMissingDates(startDate, days, counts));
    }

    @Operation(summary = "新用户注册趋势（最近N天每日新注册数）")
    @OperationLog(module = "system", action = "QUERY_NEW_USER_TREND", description = "查询新用户趋势", logArgs = false)
    @GetMapping("/new-user-trend")
    public Result<List<DailyActivityVO>> newUserTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        adminPermissionGuard.require(PERM_STATS_LIST);
        days = normalizeDays(days);
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusDays(days - 1L);
        List<DailyActivityCount> counts = loginLogMapper.selectDailyRegistrationCounts(
                startDate.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return Result.success(fillMissingDates(startDate, days, counts));
    }

    private List<DailyActivityVO> fillMissingDates(LocalDate startDate,
                                                   int days,
                                                   List<DailyActivityCount> counts) {
        Map<LocalDate, Long> countByDate = new LinkedHashMap<>();
        for (DailyActivityCount count : counts) {
            if (count != null && count.getActivityDate() != null) {
                countByDate.put(count.getActivityDate(),
                        count.getActivityCount() == null ? 0L : count.getActivityCount());
            }
        }
        List<DailyActivityVO> result = new ArrayList<>(days);
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = startDate.plusDays(offset);
            DailyActivityVO vo = new DailyActivityVO();
            vo.setDate(date.toString());
            vo.setActiveUsers(Math.toIntExact(countByDate.getOrDefault(date, 0L)));
            result.add(vo);
        }
        return result;
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return 30;
        }
        return Math.min(days, MAX_DAYS);
    }

    @Data
    public static class DailyActivityVO {
        private String date;
        private int activeUsers;
    }
}
