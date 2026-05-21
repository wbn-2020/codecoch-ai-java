package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.TargetJobSnapshot;
import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.feign.ResumeAgentContextFeignClient;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.service.AgentContextBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextBuilderImpl implements AgentContextBuilder {

    private final ResumeAgentContextFeignClient resumeFeignClient;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentMemoryMapper agentMemoryMapper;

    @Override
    public JobCoachAgentContext build(Long userId, Long targetJobId, LocalDate planDate) {
        TargetJobContextVO targetJob = resolveTargetJob(targetJobId);
        if (targetJob == null || targetJob.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.TARGET_JOB_REQUIRED);
        }
        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(userId);
        context.setTargetJobId(targetJob.getId());
        context.setPlanDate(planDate);
        context.setTargetJob(toSnapshot(targetJob, resolveAnalysis(targetJob.getId())));
        context.setRecentMemories(recentMemories(userId));
        context.setAgentHistorySummary(agentHistorySummary(userId, targetJob.getId(), planDate));
        context.getContextWarnings().add("Context currently includes target job, JD analysis, recent Agent task history, and enabled memories.");
        return context;
    }

    private TargetJobContextVO resolveTargetJob(Long targetJobId) {
        if (targetJobId != null) {
            return FeignResultUtils.unwrap(resumeFeignClient.getTargetJob(targetJobId));
        }
        return FeignResultUtils.unwrap(resumeFeignClient.currentTargetJob());
    }

    private JobDescriptionAnalysisContextVO resolveAnalysis(Long targetJobId) {
        try {
            return FeignResultUtils.unwrap(resumeFeignClient.getAnalysis(targetJobId));
        } catch (RuntimeException ex) {
            log.info("Target job analysis unavailable targetJobId={}, reason={}", targetJobId, ex.getMessage());
            return null;
        }
    }

    private TargetJobSnapshot toSnapshot(TargetJobContextVO targetJob, JobDescriptionAnalysisContextVO analysis) {
        TargetJobSnapshot snapshot = new TargetJobSnapshot();
        snapshot.setId(targetJob.getId());
        snapshot.setJobTitle(targetJob.getJobTitle());
        snapshot.setCompanyName(targetJob.getCompanyName());
        snapshot.setJobLevel(targetJob.getJobLevel());
        snapshot.setJdSource(targetJob.getJdSource());
        snapshot.setAnalysisSummary(firstText(
                analysis == null ? null : analysis.getSummary(),
                targetJob.getAnalysisSummary(),
                summarizeJd(targetJob.getJdText())));
        snapshot.setRequiredSkills(analysis != null && analysis.getRequiredSkills() != null
                ? analysis.getRequiredSkills()
                : targetJob.getRequiredSkills());
        snapshot.setInterviewFocusPoints(analysis != null && analysis.getInterviewFocusPoints() != null
                ? analysis.getInterviewFocusPoints()
                : targetJob.getInterviewFocusPoints());
        return snapshot;
    }

    private String summarizeJd(String jdText) {
        if (!StringUtils.hasText(jdText)) {
            return null;
        }
        return jdText.length() > 500 ? jdText.substring(0, 500) : jdText;
    }

    private List<String> recentMemories(Long userId) {
        return agentMemoryMapper.selectList(new LambdaQueryWrapper<AgentMemory>()
                        .eq(AgentMemory::getUserId, userId)
                        .eq(AgentMemory::getEnabled, 1)
                        .orderByDesc(AgentMemory::getUpdatedAt)
                        .last("LIMIT 10"))
                .stream()
                .map(memory -> memory.getMemoryType() + ": " + memory.getContent())
                .filter(StringUtils::hasText)
                .toList();
    }

    private String agentHistorySummary(Long userId, Long targetJobId, LocalDate planDate) {
        LocalDate start = planDate.minusDays(6);
        List<AgentTask> tasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getTargetJobId, targetJobId)
                .ge(AgentTask::getDueDate, start)
                .le(AgentTask::getDueDate, planDate));
        long done = tasks.stream().filter(task -> "DONE".equals(task.getStatus())).count();
        long skipped = tasks.stream().filter(task -> "SKIPPED".equals(task.getStatus())).count();
        return "recent7DaysTasks=" + tasks.size() + ",done=" + done + ",skipped=" + skipped;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
