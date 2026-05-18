package com.codecoachai.user.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.user.domain.dto.UpdatePasswordDTO;
import com.codecoachai.user.domain.dto.UpdateUserProfileDTO;
import com.codecoachai.user.domain.vo.UserDashboardOverviewVO;
import com.codecoachai.user.domain.vo.UserOverviewVO;
import com.codecoachai.user.domain.vo.UserProfileVO;
import com.codecoachai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        return Result.success(userService.getCurrentUserProfile());
    }

    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UpdateUserProfileDTO dto) {
        return Result.success(userService.updateCurrentUserProfile(dto));
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updateCurrentUserPassword(dto);
        return Result.success();
    }

    @GetMapping("/overview")
    public Result<UserOverviewVO> getOverview() {
        return Result.success(userService.getOverview());
    }

    @GetMapping("/dashboard/overview")
    public Result<UserDashboardOverviewVO> getDashboardOverview() {
        return Result.success(userService.getDashboardOverview());
    }
}
