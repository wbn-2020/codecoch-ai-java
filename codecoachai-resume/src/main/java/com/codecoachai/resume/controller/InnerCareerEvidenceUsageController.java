package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.InnerCareerEvidenceUsageFactsVO;
import com.codecoachai.resume.service.CareerEvidenceUsageService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/evidence-usages")
public class InnerCareerEvidenceUsageController {

    private final CareerEvidenceUsageService service;

    @GetMapping("/users/{userId}/facts")
    public Result<InnerCareerEvidenceUsageFactsVO> facts(
            @PathVariable Long userId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long usageId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dataCutoffAt) {
        return Result.success(service.innerFacts(
                userId, campaignId, applicationId, usageId, dataCutoffAt));
    }
}
