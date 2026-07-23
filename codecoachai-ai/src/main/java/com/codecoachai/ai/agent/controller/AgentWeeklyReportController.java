package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportQueryDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportRefreshDTO;
import com.codecoachai.ai.agent.domain.vo.weekly.AgentWeeklyReportVO;
import com.codecoachai.ai.agent.service.AgentWeeklyReportService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/weekly-reports")
public class AgentWeeklyReportController {

    private final AgentWeeklyReportService weeklyReportService;

    @GetMapping("/current")
    public Result<AgentWeeklyReportVO> current(
            @Valid @ModelAttribute AgentWeeklyReportQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(weeklyReportService.current(userId, query));
    }

    @PostMapping("/generate")
    public Result<AgentWeeklyReportVO> generate(
            @Valid @RequestBody(required = false) AgentWeeklyReportGenerateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(weeklyReportService.generate(userId, dto));
    }

    @GetMapping("/{reportId}")
    public Result<AgentWeeklyReportVO> detail(@PathVariable Long reportId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(weeklyReportService.detail(userId, reportId));
    }

    @GetMapping
    public Result<List<AgentWeeklyReportVO>> list(
            @Valid @ModelAttribute AgentWeeklyReportQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(weeklyReportService.list(userId, query));
    }

    @PostMapping("/{reportId}/refresh")
    public Result<AgentWeeklyReportVO> refresh(
            @PathVariable Long reportId,
            @Valid @RequestBody(required = false) AgentWeeklyReportRefreshDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(weeklyReportService.refresh(userId, reportId, dto));
    }
}
