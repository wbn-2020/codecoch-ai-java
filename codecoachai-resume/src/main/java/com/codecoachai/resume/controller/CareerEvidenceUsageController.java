package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageCreateDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultCommandDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultWriteDTO;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageResultVO;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageVO;
import com.codecoachai.resume.domain.vo.EvidenceAssetEnvelopeVO;
import com.codecoachai.resume.domain.vo.EvidenceAssetOverviewEnvelopeVO;
import com.codecoachai.resume.service.CareerEvidenceUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerEvidenceUsageController {

    private final CareerEvidenceUsageService service;

    @PostMapping("/applications/{applicationId}/evidence-usages")
    public Result<CareerEvidenceUsageVO> createUsage(
            @PathVariable Long applicationId,
            @Valid @RequestBody CareerEvidenceUsageCreateDTO request) {
        return Result.success(service.createUsage(applicationId, request));
    }

    @GetMapping("/applications/{applicationId}/evidence-usages")
    public Result<EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO>> applicationUsages(
            @PathVariable Long applicationId,
            @ModelAttribute CareerEvidenceUsageQueryDTO query) {
        return Result.success(service.listApplicationUsages(applicationId, query));
    }

    @GetMapping("/evidence-usages/{usageId}")
    public Result<CareerEvidenceUsageVO> usage(@PathVariable Long usageId) {
        return Result.success(service.usage(usageId));
    }

    @PostMapping("/evidence-usages/{usageId}/results")
    public Result<CareerEvidenceUsageResultVO> createResult(
            @PathVariable Long usageId,
            @Valid @RequestBody CareerEvidenceUsageResultWriteDTO request) {
        return Result.success(service.createResult(usageId, request));
    }

    @GetMapping("/evidence-usages/{usageId}/results")
    public Result<EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO>> usageResults(
            @PathVariable Long usageId) {
        return Result.success(service.listUsageResults(usageId));
    }

    @PostMapping("/evidence-usage-results/{resultId}/confirm")
    public Result<CareerEvidenceUsageResultVO> confirm(
            @PathVariable Long resultId,
            @Valid @RequestBody CareerEvidenceUsageResultCommandDTO request) {
        return Result.success(service.confirmResult(resultId, request));
    }

    @PostMapping("/evidence-usage-results/{resultId}/correct")
    public Result<CareerEvidenceUsageResultVO> correct(
            @PathVariable Long resultId,
            @Valid @RequestBody CareerEvidenceUsageResultCommandDTO request) {
        return Result.success(service.correctResult(resultId, request));
    }

    @PostMapping("/evidence-usage-results/{resultId}/void")
    public Result<CareerEvidenceUsageResultVO> voidResult(
            @PathVariable Long resultId,
            @Valid @RequestBody CareerEvidenceUsageResultCommandDTO request) {
        return Result.success(service.voidResult(resultId, request));
    }

    @GetMapping("/evidence-assets/overview")
    public Result<EvidenceAssetOverviewEnvelopeVO> overview(
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long applicationId) {
        return Result.success(service.overview(campaignId, applicationId));
    }

    @GetMapping("/evidence-assets/usages")
    public Result<EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO>> usages(
            @ModelAttribute CareerEvidenceUsageQueryDTO query) {
        return Result.success(service.listUsages(query));
    }

    @GetMapping("/evidence-assets/results")
    public Result<EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO>> results(
            @ModelAttribute CareerEvidenceUsageResultQueryDTO query) {
        return Result.success(service.listResults(query));
    }
}
