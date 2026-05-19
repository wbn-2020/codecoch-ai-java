package com.codecoachai.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.system.domain.entity.LoginLog;
import com.codecoachai.system.mapper.LoginLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AdminStatsController {

    private final LoginLogMapper loginLogMapper;

    @Operation(summary = "用户活跃趋势（最近N天每日登录人数）")
    @GetMapping("/user-activity-trend")
    public Result<List<DailyActivityVO>> userActivityTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        SecurityAssert.requireAdmin();
        LocalDate today = LocalDate.now();
        List<DailyActivityVO> result = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Long count = loginLogMapper.selectCount(
                    new LambdaQueryWrapper<LoginLog>()
                            .eq(LoginLog::getLoginStatus, "SUCCESS")
                            .ge(LoginLog::getLoginTime, LocalDateTime.of(date, LocalTime.MIN))
                            .lt(LoginLog::getLoginTime, LocalDateTime.of(date.plusDays(1), LocalTime.MIN)));
            DailyActivityVO vo = new DailyActivityVO();
            vo.setDate(date.toString());
            vo.setActiveUsers(count.intValue());
            result.add(vo);
        }
        return Result.success(result);
    }

    @Operation(summary = "新用户注册趋势（最近N天每日新注册数）")
    @GetMapping("/new-user-trend")
    public Result<List<DailyActivityVO>> newUserTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        SecurityAssert.requireAdmin();
        LocalDate today = LocalDate.now();
        List<DailyActivityVO> result = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Long count = loginLogMapper.selectCount(
                    new LambdaQueryWrapper<LoginLog>()
                            .eq(LoginLog::getLoginType, "REGISTER")
                            .ge(LoginLog::getLoginTime, LocalDateTime.of(date, LocalTime.MIN))
                            .lt(LoginLog::getLoginTime, LocalDateTime.of(date.plusDays(1), LocalTime.MIN)));
            DailyActivityVO vo = new DailyActivityVO();
            vo.setDate(date.toString());
            vo.setActiveUsers(count.intValue());
            result.add(vo);
        }
        return Result.success(result);
    }

    @Data
    public static class DailyActivityVO {
        private String date;
        private int activeUsers;
    }
}
