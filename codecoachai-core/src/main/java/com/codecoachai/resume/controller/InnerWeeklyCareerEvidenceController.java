package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO;
import com.codecoachai.resume.service.WeeklyCareerEvidenceService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/applications")
public class InnerWeeklyCareerEvidenceController {

    private final WeeklyCareerEvidenceService weeklyCareerEvidenceService;

    @GetMapping("/users/{userId}/weekly-evidence")
    public Result<WeeklyCareerEvidenceVO> getWeeklyEvidence(
            @PathVariable("userId") Long userId,
            @RequestParam("rangeStartUtc")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rangeStartUtc,
            @RequestParam("rangeEndUtc")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rangeEndUtc,
            @RequestParam("sourceCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sourceCutoffAt,
            @RequestParam(value = "targetJobId", required = false) Long targetJobId,
            @RequestParam("timezone") String timezone,
            @RequestParam(value = "experimentIds", required = false) List<Long> experimentIds) {
        return Result.success(weeklyCareerEvidenceService.getWeeklyEvidence(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                timezone,
                experimentIds));
    }
}
