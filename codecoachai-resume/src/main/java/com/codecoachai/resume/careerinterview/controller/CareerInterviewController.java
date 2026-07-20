package com.codecoachai.resume.careerinterview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewCalendarLinkDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewProcessCreateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRescheduleDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRoundCreateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRoundUpdateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewTransitionDTO;
import com.codecoachai.resume.careerinterview.service.CareerInterviewService;
import com.codecoachai.resume.careerinterview.vo.CareerInterviewProcessVO;
import com.codecoachai.resume.careerinterview.vo.CareerInterviewRoundVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerInterviewController {
    private final CareerInterviewService service;
    private final V7FeatureGate featureGate;

    @GetMapping("/applications/{applicationId}/interview-process")
    public Result<CareerInterviewProcessVO> get(@PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireRealInterview();
        return Result.success(service.getProcess(applicationId));
    }

    @PostMapping("/applications/{applicationId}/interview-process")
    public Result<CareerInterviewProcessVO> create(@PathVariable Long applicationId,
                                                   @Valid @RequestBody CareerInterviewProcessCreateDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireRealInterview();
        return Result.success(service.createProcess(applicationId, request));
    }

    @PostMapping("/interview-processes/{processId}/rounds")
    public Result<CareerInterviewRoundVO> createRound(@PathVariable Long processId,
                                                       @Valid @RequestBody CareerInterviewRoundCreateDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireRealInterview();
        return Result.success(service.createRound(processId, request));
    }

    @PutMapping("/interview-rounds/{roundId}")
    public Result<CareerInterviewRoundVO> update(@PathVariable Long roundId,
                                                 @Valid @RequestBody CareerInterviewRoundUpdateDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireRealInterview();
        return Result.success(service.updateRound(roundId, request));
    }

    @PostMapping("/interview-rounds/{roundId}/transitions")
    public Result<CareerInterviewRoundVO> transition(@PathVariable Long roundId,
                                                      @Valid @RequestBody CareerInterviewTransitionDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireRealInterview();
        return Result.success(service.transition(roundId, request));
    }

    @PostMapping("/interview-rounds/{roundId}/reschedule")
    public Result<CareerInterviewRoundVO> reschedule(@PathVariable Long roundId,
                                                      @Valid @RequestBody CareerInterviewRescheduleDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireRealInterview();
        return Result.success(service.reschedule(roundId, request));
    }

    @PostMapping("/interview-rounds/{roundId}/link-calendar-event")
    public Result<CareerInterviewRoundVO> linkCalendar(@PathVariable Long roundId,
                                                        @Valid @RequestBody CareerInterviewCalendarLinkDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireRealInterview();
        return Result.success(service.linkCalendarEvent(roundId, request));
    }
}
