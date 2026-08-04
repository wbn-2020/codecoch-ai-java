package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.controller.InnerStudyPlanController;
import com.codecoachai.question.feign.StudyPlanFeignClient;
import com.codecoachai.question.feign.vo.InnerStudyPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalStudyPlanFeignClient implements StudyPlanFeignClient {

    private final InnerStudyPlanController innerStudyPlanController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerStudyPlanVO> getStudyPlan(Long planId) {
        return resultMapper.value(innerStudyPlanController.getPlan(planId), InnerStudyPlanVO.class);
    }
}
