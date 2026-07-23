package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO;
import com.codecoachai.resume.domain.dto.JobApplicationEventReviewGenerateDTO;
import com.codecoachai.resume.domain.dto.JobApplicationSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeApplyAiSuggestionDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCopyDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCreateDTO;
import com.codecoachai.resume.domain.vo.JobApplicationEventVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO;
import com.codecoachai.resume.domain.vo.JobApplicationStatsVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionAdoptionVO;
import com.codecoachai.resume.domain.vo.ResumeVersionDiffVO;
import com.codecoachai.resume.domain.vo.ResumeVersionVO;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.codecoachai.resume.service.JobApplicationEventReviewService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class V4ResumeCareerController {

    private final V4ResumeCareerService v4ResumeCareerService;
    private final JobApplicationEventReviewService jobApplicationEventReviewService;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @PostMapping("/resumes/{resumeId}/versions")
    public Result<ResumeVersionVO> createVersion(@PathVariable Long resumeId,
                                                  @RequestBody(required = false) ResumeVersionCreateDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.createVersion(resumeId, dto));
    }

    @GetMapping("/resumes/{resumeId}/versions")
    public Result<List<ResumeVersionVO>> listVersions(@PathVariable Long resumeId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.listVersions(resumeId));
    }

    @PostMapping("/resumes/{resumeId}/versions/{versionId}/copy")
    public Result<ResumeVersionVO> copyVersion(@PathVariable Long resumeId,
                                                @PathVariable Long versionId,
                                                @RequestBody(required = false) ResumeVersionCopyDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.copyVersion(resumeId, versionId, dto));
    }

    @GetMapping("/resume-versions/{versionId}")
    public Result<ResumeVersionVO> getVersion(@PathVariable Long versionId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.getVersion(versionId));
    }

    @GetMapping("/resumes/{resumeId}/versions/{versionId}/diff")
    public Result<ResumeVersionDiffVO> diffVersion(@PathVariable Long resumeId, @PathVariable Long versionId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.diffVersion(resumeId, versionId));
    }

    @GetMapping("/resume-versions/{sourceVersionId}/diff/{targetVersionId}")
    public Result<ResumeVersionDiffVO> diffVersions(@PathVariable Long sourceVersionId,
                                                    @PathVariable Long targetVersionId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.diffVersions(sourceVersionId, targetVersionId));
    }

    @OperationLog(module = "resume", action = "ROLLBACK_RESUME_VERSION", description = "Rollback resume version", logArgs = false, logResponse = false)
    @PostMapping("/resumes/{resumeId}/versions/{versionId}/rollback")
    public Result<ResumeVersionVO> rollbackVersion(@PathVariable Long resumeId,
                                                   @PathVariable Long versionId,
                                                   @RequestParam(required = false) Boolean confirm,
                                                   @RequestParam(required = false) Boolean dryRun,
                                                   @RequestParam(required = false) String reason,
                                                   @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String lockKey = operationConfirmationGuard.requireConfirmed(
                "RESUME_VERSION_ROLLBACK:" + userId + ":" + resumeId + ":" + versionId,
                confirm,
                dryRun,
                reason,
                idempotencyKey);
        try {
            return Result.success(v4ResumeCareerService.rollbackVersion(resumeId, versionId));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @OperationLog(module = "resume", action = "APPLY_RESUME_VERSION_AI_SUGGESTION", description = "Apply AI suggestion to resume version", logArgs = false, logResponse = false)
    @PostMapping("/resume-versions/{versionId}/apply-ai-suggestion")
    public Result<ResumeSuggestionAdoptionVO> applyAiSuggestion(@PathVariable Long versionId,
                                                                @RequestBody(required = false) ResumeApplyAiSuggestionDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        String lockKey = operationConfirmationGuard.requireConfirmed(
                "RESUME_VERSION_APPLY_AI_SUGGESTION:" + userId + ":" + versionId,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey());
        try {
            return Result.success(v4ResumeCareerService.applyAiSuggestion(versionId, dto));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @GetMapping("/applications")
    public Result<List<JobApplicationVO>> listApplications(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size,
                                                           @RequestParam(required = false) String keyword) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.listApplications(status, page, size, keyword));
    }

    @GetMapping("/applications/stats")
    public Result<JobApplicationStatsVO> getApplicationStats() {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.getApplicationStats(LocalDateTime.now()));
    }

    @PostMapping("/applications")
    public Result<JobApplicationVO> createApplication(@RequestBody JobApplicationSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.createApplication(dto));
    }

    @PutMapping("/applications/{id}")
    public Result<JobApplicationVO> updateApplication(@PathVariable Long id, @RequestBody JobApplicationSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.updateApplication(id, dto));
    }

    @GetMapping("/applications/{id}/events")
    public Result<List<JobApplicationEventVO>> listApplicationEvents(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.listApplicationEvents(id));
    }

    @PostMapping("/applications/{id}/events")
    public Result<JobApplicationEventVO> createApplicationEvent(@PathVariable Long id,
                                                               @RequestBody JobApplicationEventSaveDTO dto) {
        SecurityAssert.requireLoginUserId();
        return Result.success(v4ResumeCareerService.createApplicationEvent(id, dto));
    }

    @OperationLog(module = "resume", action = "GENERATE_APPLICATION_EVENT_AI_REVIEW",
            description = "生成真实求职事件 AI 复盘", logResponse = false)
    @Operation(summary = "生成真实求职事件 AI 复盘")
    @PostMapping("/applications/{applicationId}/events/{eventId}/ai-review")
    public Result<JobApplicationEventStructuredReviewVO> generateApplicationEventReview(
            @PathVariable Long applicationId,
            @PathVariable Long eventId,
            @Valid @RequestBody(required = false) JobApplicationEventReviewGenerateDTO request) {
        SecurityAssert.requireLoginUserId();
        JobApplicationEventReviewGenerateDTO actual =
                request == null ? new JobApplicationEventReviewGenerateDTO() : request;
        return Result.success(jobApplicationEventReviewService.generate(applicationId, eventId, actual));
    }
}
