package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO;
import com.codecoachai.interview.service.WeeklyInterviewEvidenceService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/interviews")
public class InnerWeeklyInterviewEvidenceController {

    private final WeeklyInterviewEvidenceService weeklyInterviewEvidenceService;

    @GetMapping("/users/{userId}/weekly-evidence")
    public Result<WeeklyInterviewEvidenceVO> getWeeklyEvidence(
            @PathVariable("userId") Long userId,
            @RequestParam("rangeStartUtc")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rangeStartUtc,
            @RequestParam("rangeEndUtc")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rangeEndUtc,
            @RequestParam("sourceCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sourceCutoffAt,
            @RequestParam(value = "targetJobId", required = false) Long targetJobId,
            @RequestParam("timezone") String timezone) {
        return Result.success(weeklyInterviewEvidenceService.getWeeklyEvidence(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                timezone));
    }
}
