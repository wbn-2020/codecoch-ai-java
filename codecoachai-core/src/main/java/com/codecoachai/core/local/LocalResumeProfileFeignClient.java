package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.ResumeProfileFeignClient;
import com.codecoachai.question.feign.vo.InnerSkillProfileVO;
import com.codecoachai.resume.service.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalResumeProfileFeignClient implements ResumeProfileFeignClient {

    private final SkillProfileService skillProfileService;
    private final LocalResultMapper resultMapper;

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
}
