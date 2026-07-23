package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.domain.context.JobApplicationAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobExperimentAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.ProjectEvidenceAgentContextVO;
import com.codecoachai.ai.agent.domain.context.RequirementReadinessAgentContextVO;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResumeAgentContextFeignClientFallbackFactory
        implements FallbackFactory<ResumeAgentContextFeignClient> {

    @Override
    public ResumeAgentContextFeignClient create(Throwable cause) {
        log.warn("ResumeAgentContextFeignClient fallback triggered failureType={} reason={}",
                cause == null ? null : cause.getClass().getSimpleName(),
                safeReason(cause == null ? null : cause.getMessage()));
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

            @Override
            public Result<List<ProjectEvidenceAgentContextVO>> listProjectEvidenceAgentContext(Long userId) {
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<RequirementReadinessAgentContextVO> requirementReadinessContext(
                    Long userId, Long targetJobId) {
                RequirementReadinessAgentContextVO context = new RequirementReadinessAgentContextVO();
                context.setTargetJobId(targetJobId);
                context.setConfidenceLevel("LOW");
                context.setFallback(true);
                context.setMatrixCurrent(false);
                context.setSampleSufficient(false);
                context.setRequirementCount(0);
                context.setWarnings(List.of("RESUME_REQUIREMENT_CONTEXT_FALLBACK"));
                return Result.success(context);
            }

            @Override
            public Result<List<JobExperimentAgentContextVO>> listJobExperimentAgentContext(Long userId,
                                                                                           Long targetJobId) {
                return Result.success(Collections.emptyList());
            }
        };
    }

    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "resume agent context fallback";
        }
        return "resume agent context fallback; reasonLength=" + reason.length() + "; reasonHash=" + shortHash(reason);
    }

    private String shortHash(String value) {
        String hash = TextFingerprintUtils.sha256Hex(value);
        return hash == null ? null : hash.substring(0, Math.min(hash.length(), 12));
    }
}
