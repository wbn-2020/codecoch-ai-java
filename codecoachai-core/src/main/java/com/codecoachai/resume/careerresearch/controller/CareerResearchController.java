package com.codecoachai.resume.careerresearch.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSnapshotGenerateDTO;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSourceCreateDTO;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSourceVersionCreateDTO;
import com.codecoachai.resume.careerresearch.service.CareerResearchService;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSnapshotVO;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSourceVO;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSourceVersionVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerResearchController {
    private final CareerResearchService service;
    private final V7FeatureGate featureGate;

    @GetMapping("/applications/{applicationId}/research-sources")
    public Result<List<CareerResearchSourceVO>> listSources(@PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireResearch();
        return Result.success(service.listSources(applicationId));
    }

    @PostMapping("/applications/{applicationId}/research-sources")
    public Result<CareerResearchSourceVO> createSource(
            @PathVariable Long applicationId,
            @Valid @RequestBody CareerResearchSourceCreateDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireResearch();
        return Result.success(service.createSource(applicationId, request));
    }

    @PostMapping("/research-sources/{sourceId}/versions")
    public Result<CareerResearchSourceVersionVO> addVersion(
            @PathVariable Long sourceId,
            @Valid @RequestBody CareerResearchSourceVersionCreateDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireResearch();
        return Result.success(service.addSourceVersion(sourceId, request));
    }

    @PostMapping("/research-sources/{sourceId}/deactivate")
    public Result<Void> deactivate(@PathVariable Long sourceId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireResearch();
        service.deactivateSource(sourceId);
        return Result.success();
    }

    @PostMapping("/applications/{applicationId}/research-snapshots")
    public Result<CareerResearchSnapshotVO> generateSnapshot(
            @PathVariable Long applicationId,
            @RequestBody(required = false) CareerResearchSnapshotGenerateDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireResearch();
        return Result.success(service.generateSnapshot(applicationId,
                request == null ? new CareerResearchSnapshotGenerateDTO() : request));
    }

    @GetMapping("/applications/{applicationId}/research-snapshots/latest")
    public Result<CareerResearchSnapshotVO> latestSnapshot(@PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireResearch();
        return Result.success(service.latestSnapshot(applicationId));
    }

    @GetMapping("/research-snapshots/{snapshotId}")
    public Result<CareerResearchSnapshotVO> getSnapshot(@PathVariable Long snapshotId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireResearch();
        return Result.success(service.getSnapshot(snapshotId));
    }
}
