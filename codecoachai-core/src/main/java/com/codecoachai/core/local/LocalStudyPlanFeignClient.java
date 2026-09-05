package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.service.StudyPlanService;
import com.codecoachai.question.feign.StudyPlanFeignClient;
import com.codecoachai.question.feign.vo.InnerStudyPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalStudyPlanFeignClient implements StudyPlanFeignClient {

    private final StudyPlanService studyPlanService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerStudyPlanVO> getStudyPlan(Long planId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(planId, "planId");
            return resultMapper.value(
                    Result.success(studyPlanService.getInnerPlan(planId)),
                    InnerStudyPlanVO.class);
        });
    }
}
