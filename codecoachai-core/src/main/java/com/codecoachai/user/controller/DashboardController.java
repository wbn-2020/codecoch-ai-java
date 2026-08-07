package com.codecoachai.user.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.user.domain.vo.UserDashboardOverviewVO;
import com.codecoachai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController {

    private final UserService userService;

    @GetMapping("/overview")
    public Result<UserDashboardOverviewVO> overview() {
        return Result.success(userService.getDashboardOverview());
    }
}
