package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.domain.context.JobApplicationAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.common.core.domain.Result;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResumeAgentContextFeignClientFallbackFactory
        implements FallbackFactory<ResumeAgentContextFeignClient> {

    @Override
    public ResumeAgentContextFeignClient create(Throwable cause) {
        log.warn("ResumeAgentContextFeignClient fallback triggered: {}", cause.getMessage(), cause);
        return new ResumeAgentContextFeignClient() {

            @Override
            public Result<TargetJobContextVO> currentTargetJob(Long userId) {
                return Result.success(new TargetJobContextVO());
            }

            @Override
            public Result<TargetJobContextVO> getTargetJob(Long userId, Long id) {
                return Result.success(new TargetJobContextVO());
            }

            @Override
            public Result<JobDescriptionAnalysisContextVO> getAnalysis(Long userId, Long id) {
                return Result.success(new JobDescriptionAnalysisContextVO());
            }

            @Override
            public Result<List<JobApplicationAgentContextVO>> listAgentApplications(
                    Long userId, Long targetJobId) {
                return Result.success(Collections.emptyList());
            }
        };
    }
}
