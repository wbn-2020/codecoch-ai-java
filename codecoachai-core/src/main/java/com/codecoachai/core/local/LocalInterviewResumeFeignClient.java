package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.InterviewWeakPointFeedbackDTO;
import com.codecoachai.interview.feign.dto.JobApplicationEventSaveDTO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationPackageVO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationSummaryVO;
import com.codecoachai.interview.feign.vo.InnerProjectEvidenceTrainingContextVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeJobMatchReportVO;
import com.codecoachai.interview.feign.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.interview.feign.vo.InnerSkillProfileVO;
import com.codecoachai.interview.feign.vo.InnerTargetJobVO;
import com.codecoachai.resume.service.JobApplicationPackageService;
import com.codecoachai.resume.service.ProjectEvidenceService;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.resume.service.ResumeService;
import com.codecoachai.resume.service.SkillProfileService;
import com.codecoachai.resume.service.TargetJobService;
import com.codecoachai.resume.service.V4ResumeCareerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalInterviewResumeFeignClient implements ResumeFeignClient {

    private final ResumeService resumeService;
    private final JobApplicationPackageService jobApplicationPackageService;
    private final V4ResumeCareerService v4ResumeCareerService;
    private final TargetJobService targetJobService;
    private final ResumeJobMatchService resumeJobMatchService;
    private final SkillProfileService skillProfileService;
    private final ProjectEvidenceService projectEvidenceService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerResumeDetailVO> getResume(Long id) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            return resultMapper.value(
                    Result.success(resumeService.getInnerResume(id)),
                    InnerResumeDetailVO.class);
        });
    }

    @Override
    public Result<InnerResumeDetailVO> getDefaultResume() {
        return resultMapper.invoke(() -> resultMapper.value(
                Result.success(resumeService.getDefaultInnerResume()),
                InnerResumeDetailVO.class));
    }

    @Override
    public Result<InnerJobApplicationPackageVO> getApplicationPackage(Long id) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            SecurityAssert.requireLoginUserId();
            return resultMapper.value(
                    Result.success(jobApplicationPackageService.detail(id)),
                    InnerJobApplicationPackageVO.class);
        });
    }

    @Override
    public Result<InnerJobApplicationSummaryVO> getApplicationSummary(Long userId, Long applicationId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            resultMapper.requireParameter(applicationId, "applicationId");
            return resultMapper.value(
                    Result.success(v4ResumeCareerService.getApplicationSummaryForUser(userId, applicationId)),
                    InnerJobApplicationSummaryVO.class);
        });
    }

    @Override
    public Result<Void> createApplicationEvent(Long userId, Long applicationId, JobApplicationEventSaveDTO dto) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            resultMapper.requireParameter(applicationId, "applicationId");
            return resultMapper.empty(Result.success(v4ResumeCareerService.createApplicationEventForUser(
                    userId,
                    applicationId,
                    resultMapper.convertRequiredBody(
                            dto,
                            com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO.class))));
        });
    }

    @Override
    public Result<InnerTargetJobVO> getCurrentTargetJob(Long userId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            return resultMapper.value(
                    Result.success(targetJobService.getCurrentForUser(userId)),
                    InnerTargetJobVO.class);
        });
    }

    @Override
    public Result<InnerTargetJobVO> getTargetJob(Long userId, Long id) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            resultMapper.requireParameter(id, "id");
            return resultMapper.value(
                    Result.success(targetJobService.getTargetJobForUser(id, userId)),
                    InnerTargetJobVO.class);
        });
    }

    @Override
    public Result<InnerResumeOptimizeRecordVO> getOptimizeRecord(Long recordId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(recordId, "recordId");
            return resultMapper.value(
                    Result.success(resumeService.getInnerOptimizeRecord(recordId)),
                    InnerResumeOptimizeRecordVO.class);
        });
    }

    @Override
    public Result<InnerResumeJobMatchReportVO> getSuccessResumeJobMatchReport(Long matchReportId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(matchReportId, "matchReportId");
            return resultMapper.value(
                    Result.success(resumeJobMatchService.getInnerSuccessReport(matchReportId)),
                    InnerResumeJobMatchReportVO.class);
        });
    }

    @Override
    public Result<InnerSkillProfileVO> getSkillProfile(Long profileId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(profileId, "profileId");
            return resultMapper.value(
                    Result.success(skillProfileService.getInnerProfile(profileId)),
                    InnerSkillProfileVO.class);
        });
    }

    @Override
    public Result<InnerSkillProfileVO> getSuccessSkillProfileByMatchReport(Long matchReportId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(matchReportId, "matchReportId");
            return resultMapper.value(
                    Result.success(skillProfileService.getInnerSuccessProfileByMatchReport(matchReportId)),
                    InnerSkillProfileVO.class);
        });
    }

    @Override
    public Result<List<InnerProjectEvidenceTrainingContextVO>> listProjectEvidenceTrainingContext(
            Long userId, List<Long> ids) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            return resultMapper.values(
                    Result.success(projectEvidenceService.listTrainingContextForUser(userId, ids)),
                    InnerProjectEvidenceTrainingContextVO.class);
        });
    }

    @Override
    public Result<Void> feedbackInterviewWeakPoints(InterviewWeakPointFeedbackDTO dto) {
        return resultMapper.invoke(() -> {
            skillProfileService.feedbackInterviewWeakPoints(
                    resultMapper.convertRequiredBody(
                            dto,
                            com.codecoachai.resume.domain.dto.InterviewWeakPointFeedbackDTO.class));
            return Result.success();
        });
    }
}
