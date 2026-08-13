package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.JobSearchExperimentQueryDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentRelationSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentReviewSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentSaveDTO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentDetailVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentListVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentMetricsVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentRelationVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentReviewVO;
import com.codecoachai.resume.service.JobSearchExperimentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/job-experiments")
public class JobSearchExperimentController {

    private final JobSearchExperimentService jobSearchExperimentService;

    @GetMapping
    public Result<PageResult<JobSearchExperimentListVO>> list(@ModelAttribute JobSearchExperimentQueryDTO query) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.list(query));
    }

    @OperationLog(module = "job-experiment", action = "CREATE_JOB_EXPERIMENT", description = "Create job search experiment", logResponse = false)
    @PostMapping
    public Result<JobSearchExperimentDetailVO> create(@Valid @RequestBody JobSearchExperimentSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<JobSearchExperimentDetailVO> detail(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.detail(id));
    }

    @OperationLog(module = "job-experiment", action = "UPDATE_JOB_EXPERIMENT", description = "Update job search experiment", logResponse = false)
    @PutMapping("/{id}")
    public Result<JobSearchExperimentDetailVO> update(@PathVariable Long id,
                                                      @Valid @RequestBody JobSearchExperimentSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.update(id, dto));
    }

    @OperationLog(module = "job-experiment", action = "DELETE_JOB_EXPERIMENT", description = "Delete job search experiment", logResponse = false)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        jobSearchExperimentService.delete(id);
        return Result.success();
    }

    @OperationLog(module = "job-experiment", action = "ADD_JOB_EXPERIMENT_RELATION", description = "Add job search experiment relation", logResponse = false)
    @PostMapping("/{id}/relations")
    public Result<JobSearchExperimentRelationVO> addRelation(@PathVariable Long id,
                                                             @Valid @RequestBody JobSearchExperimentRelationSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.addRelation(id, dto));
    }

    @OperationLog(module = "job-experiment", action = "DELETE_JOB_EXPERIMENT_RELATION", description = "Delete job search experiment relation", logResponse = false)
    @DeleteMapping("/{id}/relations/{relationId}")
    public Result<Void> deleteRelation(@PathVariable Long id, @PathVariable Long relationId) {
        SecurityAssert.requireLoginUserId();
        jobSearchExperimentService.deleteRelation(id, relationId);
        return Result.success();
    }

    @GetMapping("/{id}/metrics")
    public Result<JobSearchExperimentMetricsVO> metrics(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.metrics(id));
    }

    @GetMapping("/{id}/insights")
    public Result<JobSearchExperimentMetricsVO> insights(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.metrics(id));
    }

    @GetMapping("/{id}/reviews")
    public Result<List<JobSearchExperimentReviewVO>> reviews(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.listReviews(id));
    }

    @OperationLog(module = "job-experiment", action = "CREATE_JOB_EXPERIMENT_REVIEW", description = "Create job search experiment review", logResponse = false)
    @PostMapping("/{id}/reviews")
    public Result<JobSearchExperimentReviewVO> createReview(@PathVariable Long id,
                                                            @RequestBody(required = false) JobSearchExperimentReviewSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.createReview(id, dto));
    }

    @OperationLog(module = "job-experiment", action = "CREATE_JOB_EXPERIMENT_REVIEW", description = "Create job search experiment review", logResponse = false)
    @PostMapping("/{id}/review")
    public Result<JobSearchExperimentReviewVO> createReviewAlias(@PathVariable Long id,
                                                                 @RequestBody(required = false) JobSearchExperimentReviewSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.createReview(id, dto));
    }

    @OperationLog(module = "job-experiment", action = "GENERATE_JOB_EXPERIMENT_REVIEW", description = "Generate job search experiment review", logResponse = false)
    @PostMapping("/{id}/reviews/generate")
    public Result<JobSearchExperimentReviewVO> generateReview(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(jobSearchExperimentService.generateReview(id));
    }
}
