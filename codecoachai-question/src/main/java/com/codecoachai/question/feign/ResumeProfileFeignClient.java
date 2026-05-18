package com.codecoachai.question.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.vo.InnerSkillProfileVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-resume")
public interface ResumeProfileFeignClient {

    @GetMapping("/inner/skill-profiles/{profileId}")
    Result<InnerSkillProfileVO> getSkillProfile(@PathVariable("profileId") Long profileId);

    @GetMapping("/inner/skill-profiles/by-match-report/{matchReportId}")
    Result<InnerSkillProfileVO> getSuccessSkillProfileByMatchReport(@PathVariable("matchReportId") Long matchReportId);
}
