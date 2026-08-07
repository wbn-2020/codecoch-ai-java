package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.ApplicationPackageActionExecuteDTO;
import com.codecoachai.resume.domain.dto.ApplicationPackageCreateApplicationDTO;
import com.codecoachai.resume.domain.dto.ApplicationPackageSaveDTO;
import com.codecoachai.resume.domain.vo.ApplicationPackageActionExecuteVO;
import com.codecoachai.resume.domain.vo.JobApplicationPackageListItemVO;
import com.codecoachai.resume.domain.vo.JobApplicationPackageVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.service.JobApplicationPackageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobApplicationPackageController {

    private final JobApplicationPackageService jobApplicationPackageService;

    @PostMapping("/application-packages")
    public Result<JobApplicationPackageVO> save(@RequestBody(required = false) ApplicationPackageSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobApplicationPackageService.save(dto));
    }

    @GetMapping("/application-packages/{id}")
    public Result<JobApplicationPackageVO> detail(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobApplicationPackageService.detail(id));
    }

    @GetMapping("/application-packages")
    public Result<PageResult<JobApplicationPackageListItemVO>> list(@RequestParam(required = false) Long pageNo,
                                                                    @RequestParam(required = false) Long pageSize,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(required = false) String keyword) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobApplicationPackageService.list(pageNo, pageSize, status, keyword));
    }

    @PostMapping("/application-packages/{id}/refresh")
    public Result<JobApplicationPackageVO> refresh(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobApplicationPackageService.refresh(id));
    }

    @PostMapping("/application-packages/{id}/actions/{actionCode}/execute")
    public Result<ApplicationPackageActionExecuteVO> executeAction(@PathVariable Long id,
                                                                   @PathVariable String actionCode,
                                                                   @RequestBody(required = false) ApplicationPackageActionExecuteDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobApplicationPackageService.executeAction(id, actionCode, dto));
    }

    @GetMapping("/application-packages/preview")
    public Result<JobApplicationPackageVO> preview(@RequestParam(required = false) Long targetJobId,
                                                   @RequestParam(required = false) Long jdAnalysisId,
                                                   @RequestParam(required = false) Long resumeVersionId,
                                                   @RequestParam(required = false) Long matchReportId,
                                                   @RequestParam(required = false) List<Long> projectEvidenceIds) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobApplicationPackageService.preview(
                targetJobId, jdAnalysisId, resumeVersionId, matchReportId, projectEvidenceIds));
    }

    @PostMapping("/application-packages/preview/create-application")
    public Result<JobApplicationVO> createApplication(@RequestBody(required = false) ApplicationPackageCreateApplicationDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobApplicationPackageService.createApplicationFromPreview(dto));
    }
}
