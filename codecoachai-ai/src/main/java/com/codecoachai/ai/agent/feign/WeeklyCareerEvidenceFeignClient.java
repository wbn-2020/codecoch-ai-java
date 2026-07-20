package com.codecoachai.ai.agent.feign;

import com.codecoachai.common.core.domain.Result;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-resume", contextId = "weeklyCareerEvidenceFeignClient")
public interface WeeklyCareerEvidenceFeignClient {

    @GetMapping("/inner/applications/users/{userId}/weekly-evidence")
    Result<WeeklyCareerEvidenceVO> getWeeklyEvidence(
            @PathVariable("userId") Long userId,
            @RequestParam("rangeStartUtc")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rangeStartUtc,
            @RequestParam("rangeEndUtc")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rangeEndUtc,
            @RequestParam("sourceCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sourceCutoffAt,
            @RequestParam(value = "targetJobId", required = false) Long targetJobId,
            @RequestParam("timezone") String timezone,
            @RequestParam(value = "experimentIds", required = false) List<Long> experimentIds);
}
