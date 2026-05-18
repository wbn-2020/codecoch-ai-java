package com.codecoachai.question.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.vo.InnerStudyPlanVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-interview")
public interface StudyPlanFeignClient {

    @GetMapping("/inner/study-plans/{planId}")
    Result<InnerStudyPlanVO> getStudyPlan(@PathVariable("planId") Long planId);
}
