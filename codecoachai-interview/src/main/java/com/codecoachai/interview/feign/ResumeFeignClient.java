package com.codecoachai.interview.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.vo.InnerSkillProfileVO;
import com.codecoachai.interview.feign.dto.InterviewWeakPointFeedbackDTO;
import com.codecoachai.interview.feign.dto.JobApplicationEventSaveDTO;
import com.codecoachai.interview.feign.vo.InnerProjectEvidenceTrainingContextVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationSummaryVO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationPackageVO;
import com.codecoachai.interview.feign.vo.InnerResumeJobMatchReportVO;
import com.codecoachai.interview.feign.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.interview.feign.vo.InnerTargetJobVO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-resume")
public interface ResumeFeignClient {

    @GetMapping("/inner/resumes/{id}")
    Result<InnerResumeDetailVO> getResume(@PathVariable("id") Long id);

    @GetMapping("/inner/resumes/default")
    Result<InnerResumeDetailVO> getDefaultResume();

    @GetMapping("/application-packages/{id}")
    Result<InnerJobApplicationPackageVO> getApplicationPackage(@PathVariable("id") Long id);

    @GetMapping("/inner/applications/users/{userId}/applications/{applicationId}/summary")
    Result<InnerJobApplicationSummaryVO> getApplicationSummary(@PathVariable("userId") Long userId,
                                                               @PathVariable("applicationId") Long applicationId);

    @PostMapping("/inner/applications/users/{userId}/applications/{applicationId}/events")
    Result<Void> createApplicationEvent(@PathVariable("userId") Long userId,
                                        @PathVariable("applicationId") Long applicationId,
                                        @RequestBody JobApplicationEventSaveDTO dto);

    @GetMapping("/inner/job-targets/users/{userId}/current")
    Result<InnerTargetJobVO> getCurrentTargetJob(@PathVariable("userId") Long userId);

    @GetMapping("/inner/job-targets/users/{userId}/{id}")
    Result<InnerTargetJobVO> getTargetJob(@PathVariable("userId") Long userId, @PathVariable("id") Long id);

    @GetMapping("/inner/resumes/optimize-records/{recordId}")
    Result<InnerResumeOptimizeRecordVO> getOptimizeRecord(@PathVariable("recordId") Long recordId);

    @GetMapping("/inner/resume-job-match/reports/{matchReportId}/success")
    Result<InnerResumeJobMatchReportVO> getSuccessResumeJobMatchReport(@PathVariable("matchReportId") Long matchReportId);

    @GetMapping("/inner/skill-profiles/{profileId}")
    Result<InnerSkillProfileVO> getSkillProfile(@PathVariable("profileId") Long profileId);

    @GetMapping("/inner/skill-profiles/by-match-report/{matchReportId}")
    Result<InnerSkillProfileVO> getSuccessSkillProfileByMatchReport(@PathVariable("matchReportId") Long matchReportId);

    @GetMapping("/inner/project-evidence/users/{userId}/training-context")
    Result<List<InnerProjectEvidenceTrainingContextVO>> listProjectEvidenceTrainingContext(
            @PathVariable("userId") Long userId,
            @RequestParam("ids") List<Long> ids);

    @PostMapping("/inner/skill-profiles/interview-feedback")
    Result<Void> feedbackInterviewWeakPoints(@RequestBody InterviewWeakPointFeedbackDTO dto);
}
