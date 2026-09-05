package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.JobReadinessPageQueryDTO;
import com.codecoachai.resume.domain.dto.JobReadinessQueryDTO;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.domain.vo.JobRequirementMaterializationVO;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.domain.vo.JobRequirementVO;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.JobRequirementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/job-targets/{targetJobId}")
public class JobRequirementReadinessController {

    private final JobRequirementService jobRequirementService;
    private final JobReadinessService jobReadinessService;

    @PostMapping("/requirements/materialize")
    public Result<JobRequirementMaterializationVO> materialize(@PathVariable Long targetJobId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobRequirementService.materialize(targetJobId));
    }

    @GetMapping("/requirements")
    public Result<List<JobRequirementVO>> requirements(@PathVariable Long targetJobId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobRequirementService.list(targetJobId));
    }

    @PostMapping("/requirement-matrix/refresh")
    public Result<JobRequirementMatrixVO> refreshMatrix(@PathVariable Long targetJobId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobRequirementService.refreshMatrix(targetJobId));
    }

    @GetMapping("/requirement-matrix")
    public Result<JobRequirementMatrixVO> matrix(@PathVariable Long targetJobId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobRequirementService.getMatrix(targetJobId));
    }

    @PostMapping("/readiness-snapshots")
    public Result<JobReadinessSnapshotVO> createSnapshot(@PathVariable Long targetJobId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobReadinessService.createSnapshot(targetJobId));
    }

    @GetMapping("/readiness-snapshots/latest")
    public Result<JobReadinessSnapshotVO> latestSnapshot(@PathVariable Long targetJobId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobReadinessService.latest(targetJobId));
    }

    @GetMapping("/readiness-snapshots/page")
    public Result<PageResult<JobReadinessSnapshotVO>> snapshotPage(
            @PathVariable Long targetJobId,
            @Valid @ModelAttribute JobReadinessPageQueryDTO query) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobReadinessService.page(targetJobId, query.getPageNo(), query.getPageSize()));
    }

    @GetMapping("/readiness-snapshots/{snapshotId}")
    public Result<JobReadinessSnapshotVO> snapshot(@PathVariable Long targetJobId,
                                                   @PathVariable Long snapshotId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobReadinessService.getSnapshot(targetJobId, snapshotId));
    }

    @GetMapping("/readiness-snapshots")
    public Result<List<JobReadinessSnapshotVO>> snapshots(@PathVariable Long targetJobId,
                                                          @ModelAttribute JobReadinessQueryDTO query) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobReadinessService.list(targetJobId, query));
    }
}
