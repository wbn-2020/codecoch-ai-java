package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.PortfolioRehearsalSessionSaveDTO;
import com.codecoachai.resume.domain.vo.PortfolioDemoStatusVO;
import com.codecoachai.resume.domain.vo.PortfolioDemoStorylineVO;
import com.codecoachai.resume.domain.vo.PortfolioRehearsalSessionVO;
import com.codecoachai.resume.service.PortfolioDemoService;
import com.codecoachai.resume.service.PortfolioRehearsalSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/portfolio-demo")
public class PortfolioDemoController {

    private final PortfolioDemoService portfolioDemoService;
    private final PortfolioRehearsalSessionService rehearsalSessionService;

    @GetMapping("/status")
    public Result<PortfolioDemoStatusVO> status() {
        SecurityAssert.requireLoginUserId();
        return Result.success(portfolioDemoService.status());
    }

    @OperationLog(module = "portfolio-demo", action = "LOAD_PORTFOLIO_DEMO", description = "Load portfolio demo dataset", logResponse = false)
    @PostMapping("/load")
    public Result<PortfolioDemoStatusVO> load() {
        SecurityAssert.requireLoginUserId();
        return Result.success(portfolioDemoService.load());
    }

    @OperationLog(module = "portfolio-demo", action = "RESET_PORTFOLIO_DEMO", description = "Reset portfolio demo dataset", logResponse = false)
    @PostMapping("/reset")
    public Result<PortfolioDemoStatusVO> reset() {
        SecurityAssert.requireLoginUserId();
        return Result.success(portfolioDemoService.reset());
    }

    @GetMapping("/storyline")
    public Result<PortfolioDemoStorylineVO> storyline() {
        SecurityAssert.requireLoginUserId();
        return Result.success(portfolioDemoService.storyline());
    }

    @GetMapping("/rehearsal-session")
    public Result<PortfolioRehearsalSessionVO> rehearsalSession() {
        SecurityAssert.requireLoginUserId();
        return Result.success(rehearsalSessionService.current());
    }

    @OperationLog(module = "portfolio-demo", action = "SAVE_REHEARSAL_SESSION", description = "Save portfolio rehearsal session state", logResponse = false)
    @PostMapping("/rehearsal-session")
    public Result<PortfolioRehearsalSessionVO> saveRehearsalSession(
            @Valid @RequestBody PortfolioRehearsalSessionSaveDTO request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(rehearsalSessionService.save(request));
    }

    @OperationLog(module = "portfolio-demo", action = "RESET_REHEARSAL_SESSION", description = "Reset portfolio rehearsal session state", logResponse = false)
    @PostMapping("/rehearsal-session/reset")
    public Result<PortfolioRehearsalSessionVO> resetRehearsalSession() {
        SecurityAssert.requireLoginUserId();
        return Result.success(rehearsalSessionService.reset());
    }
}
