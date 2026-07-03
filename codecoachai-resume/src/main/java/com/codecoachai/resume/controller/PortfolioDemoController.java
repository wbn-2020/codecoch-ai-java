package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.vo.PortfolioDemoStatusVO;
import com.codecoachai.resume.domain.vo.PortfolioDemoStorylineVO;
import com.codecoachai.resume.service.PortfolioDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/portfolio-demo")
public class PortfolioDemoController {

    private final PortfolioDemoService portfolioDemoService;

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
}
