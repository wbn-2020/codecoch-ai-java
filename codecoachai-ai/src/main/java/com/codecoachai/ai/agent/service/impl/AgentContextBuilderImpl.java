package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.ApplicationSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.JobExperimentSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.MemoryReference;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.ProjectEvidenceSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.TargetJobSnapshot;
import com.codecoachai.ai.agent.domain.context.JobApplicationAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.JobExperimentAgentContextVO;
import com.codecoachai.ai.agent.domain.context.ProjectEvidenceAgentContextVO;
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
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.feign.util.FeignResultUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextBuilderImpl implements AgentContextBuilder {

    private static final BigDecimal MIN_STRONG_MEMORY_CONFIDENCE = BigDecimal.valueOf(0.6);
    private static final String USER_CONFIRMED_MEMORY_SOURCE_PREFIX = "USER_CONFIRMED_";

    private final ResumeAgentContextFeignClient resumeFeignClient;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentMemoryMapper agentMemoryMapper;

    @Override
    public JobCoachAgentContext build(Long userId, Long targetJobId, LocalDate planDate) {
        TargetJobContextVO targetJob = resolveTargetJob(userId, targetJobId);
        if (targetJob == null || targetJob.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.TARGET_JOB_REQUIRED);
        }
        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(userId);
        context.setTargetJobId(targetJob.getId());
        context.setPlanDate(planDate);
        context.setTargetJob(toSnapshot(targetJob, resolveAnalysis(userId, targetJob.getId())));
        context.setApplications(resolveApplications(userId, targetJob.getId(), context));
        context.setProjectEvidences(resolveProjectEvidences(userId, context));
        context.setJobExperiments(resolveJobExperiments(userId, targetJob.getId(), context));
        List<AgentMemory> recentMemoryRecords = recentMemoryRecords(userId);
        context.setRecentMemories(recentMemoryRecords.stream()
                .map(memory -> memory.getMemoryType() + ": " + memory.getContent())
                .filter(StringUtils::hasText)
                .toList());
        context.setRecentMemoryReferences(recentMemoryRecords.stream()
                .map(this::toMemoryReference)
                .toList());
        context.setAgentHistorySummary(agentHistorySummary(userId, targetJob.getId(), planDate));
        context.getContextWarnings().add("上下文已包含目标岗位、JD 分析、近期计划任务和已启用记忆。");
        return context;
    }

    private TargetJobContextVO resolveTargetJob(Long userId, Long targetJobId) {
        if (targetJobId != null) {
            return FeignResultUtils.unwrap(resumeFeignClient.getTargetJob(userId, targetJobId));
        }
        return FeignResultUtils.unwrap(resumeFeignClient.currentTargetJob(userId));
    }

    private JobDescriptionAnalysisContextVO resolveAnalysis(Long userId, Long targetJobId) {
        try {
            return FeignResultUtils.unwrap(resumeFeignClient.getAnalysis(userId, targetJobId));
        } catch (RuntimeException ex) {
            log.info("Target job analysis unavailable targetJobId={}, reason={}", targetJobId, ex.getMessage());
            return null;
        }
    }

    private List<ApplicationSnapshot> resolveApplications(Long userId, Long targetJobId, JobCoachAgentContext context) {
        try {
            List<JobApplicationAgentContextVO> applications = FeignResultUtils.unwrap(
                    resumeFeignClient.listAgentApplications(userId, targetJobId));
            if (applications == null || applications.isEmpty()) {
                return List.of();
            }
            return applications.stream()
                    .filter(Objects::nonNull)
                    .map(this::toApplicationSnapshot)
                    .toList();
        } catch (RuntimeException ex) {
            log.info("Application context unavailable targetJobId={}, reason={}", targetJobId, ex.getMessage());
            context.getContextWarnings().add("投递上下文暂不可用，已跳过投递跟进候选任务。");
            return List.of();
        }
    }

    private ApplicationSnapshot toApplicationSnapshot(JobApplicationAgentContextVO application) {
        ApplicationSnapshot snapshot = new ApplicationSnapshot();
        snapshot.setId(application.getId());
        snapshot.setTargetJobId(application.getTargetJobId());
        snapshot.setResumeVersionId(application.getResumeVersionId());
        snapshot.setResumeId(application.getResumeId());
        snapshot.setResumeVersionNo(application.getResumeVersionNo());
        snapshot.setResumeVersionName(application.getResumeVersionName());
        snapshot.setResumeVersionCurrentFlag(application.getResumeVersionCurrentFlag());
        snapshot.setMatchReportId(application.getMatchReportId());
        snapshot.setCompanyName(application.getCompanyName());
        snapshot.setJobTitle(application.getJobTitle());
        snapshot.setSource(application.getSource());
        snapshot.setStatus(application.getStatus());
        snapshot.setAppliedAt(application.getAppliedAt());
        snapshot.setNextFollowUpAt(application.getNextFollowUpAt());
        snapshot.setFollowUpOverdue(application.getFollowUpOverdue());
        snapshot.setFollowUpDueToday(application.getFollowUpDueToday());
        snapshot.setDaysUntilFollowUp(application.getDaysUntilFollowUp());
        snapshot.setNote(application.getNote());
        snapshot.setLatestEventId(application.getLatestEventId());
        snapshot.setLatestEventType(application.getLatestEventType());
        snapshot.setLatestEventTime(application.getLatestEventTime());
        snapshot.setLatestEventSummary(application.getLatestEventSummary());
        snapshot.setCreatedAt(application.getCreatedAt());
        snapshot.setUpdatedAt(application.getUpdatedAt());
        return snapshot;
    }

    private List<ProjectEvidenceSnapshot> resolveProjectEvidences(Long userId, JobCoachAgentContext context) {
        try {
            List<ProjectEvidenceAgentContextVO> projects = FeignResultUtils.unwrap(
                    resumeFeignClient.listProjectEvidenceAgentContext(userId));
            if (projects == null || projects.isEmpty()) {
                return List.of();
            }
            return projects.stream()
                    .filter(Objects::nonNull)
                    .map(this::toProjectEvidenceSnapshot)
                    .toList();
        } catch (RuntimeException ex) {
            log.info("Project evidence context unavailable userId={}, reason={}", userId, ex.getMessage());
            context.getContextWarnings().add("Project evidence context is temporarily unavailable; skipped project evidence tasks.");
            return List.of();
        }
    }

    private ProjectEvidenceSnapshot toProjectEvidenceSnapshot(ProjectEvidenceAgentContextVO project) {
        ProjectEvidenceSnapshot snapshot = new ProjectEvidenceSnapshot();
        snapshot.setProjectEvidenceId(project.getProjectEvidenceId());
        snapshot.setTitle(project.getTitle());
        snapshot.setTechStack(project.getTechStack());
        snapshot.setCompletenessScore(project.getCompletenessScore());
        snapshot.setMissingFields(project.getMissingFields() == null ? List.of() : project.getMissingFields());
        snapshot.setSkillEvidenceCount(project.getSkillEvidenceCount());
        snapshot.setTopSkillNames(project.getTopSkillNames() == null ? List.of() : project.getTopSkillNames());
        snapshot.setTargetJobId(project.getTargetJobId());
        snapshot.setSuggestedActionPath(project.getSuggestedActionPath());
        return snapshot;
    }

    private List<JobExperimentSnapshot> resolveJobExperiments(Long userId, Long targetJobId,
                                                              JobCoachAgentContext context) {
        try {
            List<JobExperimentAgentContextVO> experiments = FeignResultUtils.unwrap(
                    resumeFeignClient.listJobExperimentAgentContext(userId, targetJobId));
            if (experiments == null || experiments.isEmpty()) {
                return List.of();
            }
            return experiments.stream()
                    .filter(Objects::nonNull)
                    .map(this::toJobExperimentSnapshot)
                    .toList();
        } catch (RuntimeException ex) {
            log.info("Job experiment context unavailable userId={}, targetJobId={}, reason={}",
                    userId, targetJobId, ex.getMessage());
            context.getContextWarnings().add("求职实验上下文暂不可用，已跳过实验复盘候选任务。");
            return List.of();
        }
    }

    private JobExperimentSnapshot toJobExperimentSnapshot(JobExperimentAgentContextVO experiment) {
        JobExperimentSnapshot snapshot = new JobExperimentSnapshot();
        snapshot.setId(experiment.getId());
        snapshot.setTitle(experiment.getTitle());
        snapshot.setTargetDirection(experiment.getTargetDirection());
        snapshot.setStatus(experiment.getStatus());
        snapshot.setSampleCount(experiment.getSampleCount());
        snapshot.setConfidenceLevel(experiment.getConfidenceLevel());
        snapshot.setSampleWarning(experiment.getSampleWarning());
        snapshot.setNextStrategy(experiment.getNextStrategy());
        return snapshot;
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

    private List<AgentMemory> recentMemoryRecords(Long userId) {
        return agentMemoryMapper.selectList(new LambdaQueryWrapper<AgentMemory>()
                        .eq(AgentMemory::getUserId, userId)
                        .eq(AgentMemory::getEnabled, 1)
                        .eq(AgentMemory::getDeleted, 0)
                        .orderByDesc(AgentMemory::getUpdatedAt)
                        .last("LIMIT 30"))
                .stream()
                .filter(this::canEnterAgentContext)
                .filter(memory -> StringUtils.hasText(memory.getContent()))
                .limit(10)
                .toList();
    }

    private MemoryReference toMemoryReference(AgentMemory memory) {
        MemoryReference reference = new MemoryReference();
        reference.setId(memory.getId());
        reference.setMemoryType(memory.getMemoryType());
        reference.setSourceType(memory.getSourceType());
        reference.setSourceId(memory.getSourceId());
        reference.setConfidence(memory.getConfidence());
        reference.setSnapshotHash(TextFingerprintUtils.sha256Hex("agent-memory:" + memory.getId() + ":"
                + firstText(memory.getSourceType(), "") + ":" + firstText(String.valueOf(memory.getUpdatedAt()), "")));
        return reference;
    }

    private boolean canEnterAgentContext(AgentMemory memory) {
        if (memory == null || memory.getEnabled() == null || memory.getEnabled() != 1) {
            return false;
        }
        if (memory.getConfidence() == null
                || memory.getConfidence().compareTo(MIN_STRONG_MEMORY_CONFIDENCE) < 0) {
            return false;
        }
        if (isManualMemorySource(memory.getSourceType())) {
            return true;
        }
        return isUserConfirmedMemorySource(memory.getSourceType());
    }

    private boolean isManualMemorySource(String sourceType) {
        String normalized = sourceType == null ? "MANUAL" : sourceType.trim().toUpperCase(Locale.ROOT);
        return List.of("MANUAL", "USER_MANUAL", "USER_NOTE").contains(normalized);
    }

    private boolean isUserConfirmedMemorySource(String sourceType) {
        return sourceType != null
                && sourceType.trim().toUpperCase(Locale.ROOT).startsWith(USER_CONFIRMED_MEMORY_SOURCE_PREFIX);
    }

    private String agentHistorySummary(Long userId, Long targetJobId, LocalDate planDate) {
        LocalDate start = planDate.minusDays(6);
        List<AgentTask> tasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getTargetJobId, targetJobId)
                .eq(AgentTask::getDeleted, 0)
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
