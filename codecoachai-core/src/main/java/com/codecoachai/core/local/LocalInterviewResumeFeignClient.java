package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
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
import com.codecoachai.resume.controller.InnerJobApplicationController;
import com.codecoachai.resume.controller.InnerProjectEvidenceController;
import com.codecoachai.resume.controller.InnerResumeController;
import com.codecoachai.resume.controller.InnerResumeJobMatchController;
import com.codecoachai.resume.controller.InnerSkillProfileController;
import com.codecoachai.resume.controller.InnerTargetJobController;
import com.codecoachai.resume.controller.JobApplicationPackageController;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalInterviewResumeFeignClient implements ResumeFeignClient {

    private final InnerResumeController innerResumeController;
    private final JobApplicationPackageController jobApplicationPackageController;
    private final InnerJobApplicationController innerJobApplicationController;
    private final InnerTargetJobController innerTargetJobController;
    private final InnerResumeJobMatchController innerResumeJobMatchController;
    private final InnerSkillProfileController innerSkillProfileController;
    private final InnerProjectEvidenceController innerProjectEvidenceController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerResumeDetailVO> getResume(Long id) {
        return resultMapper.value(innerResumeController.getResume(id), InnerResumeDetailVO.class);
    }

    @Override
    public Result<InnerResumeDetailVO> getDefaultResume() {
        return resultMapper.value(innerResumeController.getDefaultResume(), InnerResumeDetailVO.class);
    }

    @Override
    public Result<InnerJobApplicationPackageVO> getApplicationPackage(Long id) {
        return resultMapper.value(jobApplicationPackageController.detail(id), InnerJobApplicationPackageVO.class);
    }

    @Override
    public Result<InnerJobApplicationSummaryVO> getApplicationSummary(Long userId, Long applicationId) {
        return resultMapper.value(
                innerJobApplicationController.getApplicationSummary(userId, applicationId),
                InnerJobApplicationSummaryVO.class);
    }

    @Override
    public Result<Void> createApplicationEvent(Long userId, Long applicationId, JobApplicationEventSaveDTO dto) {
        return resultMapper.empty(innerJobApplicationController.createApplicationEvent(
                userId,
                applicationId,
                resultMapper.convert(dto, com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO.class)));
    }

    @Override
    public Result<InnerTargetJobVO> getCurrentTargetJob(Long userId) {
        return resultMapper.value(innerTargetJobController.current(userId), InnerTargetJobVO.class);
    }

    @Override
    public Result<InnerTargetJobVO> getTargetJob(Long userId, Long id) {
        return resultMapper.value(innerTargetJobController.detail(userId, id), InnerTargetJobVO.class);
    }

    @Override
    public Result<InnerResumeOptimizeRecordVO> getOptimizeRecord(Long recordId) {
        return resultMapper.value(innerResumeController.getOptimizeRecord(recordId), InnerResumeOptimizeRecordVO.class);
    }

    @Override
    public Result<InnerResumeJobMatchReportVO> getSuccessResumeJobMatchReport(Long matchReportId) {
        return resultMapper.value(
                innerResumeJobMatchController.getSuccessReport(matchReportId),
                InnerResumeJobMatchReportVO.class);
    }

    @Override
    public Result<InnerSkillProfileVO> getSkillProfile(Long profileId) {
        return resultMapper.value(innerSkillProfileController.getProfile(profileId), InnerSkillProfileVO.class);
    }

    @Override
    public Result<InnerSkillProfileVO> getSuccessSkillProfileByMatchReport(Long matchReportId) {
        return resultMapper.value(
                innerSkillProfileController.getSuccessByMatchReport(matchReportId),
                InnerSkillProfileVO.class);
    }

    @Override
    public Result<List<InnerProjectEvidenceTrainingContextVO>> listProjectEvidenceTrainingContext(
            Long userId, List<Long> ids) {
        return resultMapper.values(
                innerProjectEvidenceController.trainingContext(userId, ids),
                InnerProjectEvidenceTrainingContextVO.class);
    }

    @Override
    public Result<Void> feedbackInterviewWeakPoints(InterviewWeakPointFeedbackDTO dto) {
        return resultMapper.empty(innerSkillProfileController.feedbackInterviewWeakPoints(
                resultMapper.convert(dto, com.codecoachai.resume.domain.dto.InterviewWeakPointFeedbackDTO.class)));
    }
}
