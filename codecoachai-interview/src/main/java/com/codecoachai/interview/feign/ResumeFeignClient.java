package com.codecoachai.interview.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.vo.InnerSkillProfileVO;
import com.codecoachai.interview.feign.dto.InterviewWeakPointFeedbackDTO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeOptimizeRecordVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-resume")
public interface ResumeFeignClient {

    @GetMapping("/inner/resumes/{id}")
    Result<InnerResumeDetailVO> getResume(@PathVariable("id") Long id);

    @GetMapping("/inner/resumes/default")
    Result<InnerResumeDetailVO> getDefaultResume();

    @GetMapping("/inner/resumes/optimize-records/{recordId}")
    Result<InnerResumeOptimizeRecordVO> getOptimizeRecord(@PathVariable("recordId") Long recordId);

    @GetMapping("/inner/skill-profiles/{profileId}")
    Result<InnerSkillProfileVO> getSkillProfile(@PathVariable("profileId") Long profileId);

    @GetMapping("/inner/skill-profiles/by-match-report/{matchReportId}")
    Result<InnerSkillProfileVO> getSuccessSkillProfileByMatchReport(@PathVariable("matchReportId") Long matchReportId);

    @PostMapping("/inner/skill-profiles/interview-feedback")
    Result<Void> feedbackInterviewWeakPoints(@RequestBody InterviewWeakPointFeedbackDTO dto);
}
