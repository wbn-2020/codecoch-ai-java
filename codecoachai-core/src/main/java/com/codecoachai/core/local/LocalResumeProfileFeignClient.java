package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.ResumeProfileFeignClient;
import com.codecoachai.question.feign.vo.InnerSkillProfileVO;
import com.codecoachai.resume.controller.InnerSkillProfileController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalResumeProfileFeignClient implements ResumeProfileFeignClient {

    private final InnerSkillProfileController innerSkillProfileController;
    private final LocalResultMapper resultMapper;

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
}
