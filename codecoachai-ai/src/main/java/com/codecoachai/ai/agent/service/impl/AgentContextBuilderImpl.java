package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.ApplicationSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.JobExperimentSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.MemoryReference;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.PersonalKnowledgeReference;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.ProjectEvidenceSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.RequirementReadinessSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.MissingRequirementSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.TargetJobSnapshot;
import com.codecoachai.ai.agent.domain.context.JobApplicationAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.JobExperimentAgentContextVO;
import com.codecoachai.ai.agent.domain.context.ProjectEvidenceAgentContextVO;
import com.codecoachai.ai.agent.domain.context.RequirementReadinessAgentContextVO;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeChunk;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocument;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.feign.ResumeAgentContextFeignClient;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeChunkMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeDocumentMapper;
import com.codecoachai.ai.agent.service.AgentContextBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.feign.util.FeignResultUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String SOURCE_KNOWLEDGE_CHUNK = "KNOWLEDGE_CHUNK";
    private static final BigDecimal PERSONAL_KNOWLEDGE_CONTEXT_CONFIDENCE = BigDecimal.valueOf(0.75);
    private static final int MAX_PERSONAL_KNOWLEDGE_HINTS = 6;
    private static final int MAX_PERSONAL_KNOWLEDGE_HINT_LENGTH = 220;

    private final ResumeAgentContextFeignClient resumeFeignClient;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentMemoryMapper agentMemoryMapper;
    private final PersonalKnowledgeDocumentMapper personalKnowledgeDocumentMapper;
    private final PersonalKnowledgeChunkMapper personalKnowledgeChunkMapper;

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
        context.setRequirementReadiness(resolveRequirementReadiness(userId, targetJob.getId(), context));
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
        List<PersonalKnowledgeReference> knowledgeReferences = personalKnowledgeReferences(userId, context);
        context.setPersonalKnowledgeReferences(knowledgeReferences);
        context.setPersonalKnowledgeHints(knowledgeReferences.stream()
                .map(this::toPersonalKnowledgeHint)
                .filter(StringUtils::hasText)
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

    private RequirementReadinessSnapshot resolveRequirementReadiness(Long userId, Long targetJobId,
                                                                     JobCoachAgentContext context) {
        try {
            RequirementReadinessAgentContextVO source = FeignResultUtils.unwrap(
                    resumeFeignClient.requirementReadinessContext(userId, targetJobId));
            if (source == null) {
                context.getContextWarnings().add("Requirement readiness context is unavailable.");
                return null;
            }
            RequirementReadinessSnapshot snapshot = toRequirementReadinessSnapshot(source);
            if (Boolean.TRUE.equals(snapshot.getFallback())
                    || !Boolean.TRUE.equals(snapshot.getMatrixCurrent())
                    || !Boolean.TRUE.equals(snapshot.getSampleSufficient())
                    || "LOW".equalsIgnoreCase(snapshot.getConfidenceLevel())) {
                context.getContextWarnings().add(
                        "Requirement-driven tasks are gated because readiness evidence is degraded.");
            }
            return snapshot;
        } catch (RuntimeException ex) {
            log.info("Requirement readiness context unavailable userId={}, targetJobId={}, reason={}",
                    userId, targetJobId, ex.getMessage());
            context.getContextWarnings().add(
                    "Requirement readiness context is temporarily unavailable; skipped requirement-driven tasks.");
            return null;
        }
    }

    private RequirementReadinessSnapshot toRequirementReadinessSnapshot(
            RequirementReadinessAgentContextVO source) {
        RequirementReadinessSnapshot snapshot = new RequirementReadinessSnapshot();
        snapshot.setTargetJobId(source.getTargetJobId());
        snapshot.setJdAnalysisId(source.getJdAnalysisId());
        snapshot.setSnapshotId(source.getSnapshotId());
        snapshot.setSnapshotHash(source.getSnapshotHash());
        snapshot.setPolicyVersion(source.getPolicyVersion());
        snapshot.setGeneratedAt(source.getGeneratedAt());
        snapshot.setReadinessScore(source.getReadinessScore());
        snapshot.setReadinessLevel(source.getReadinessLevel());
        snapshot.setConfidenceLevel(source.getConfidenceLevel());
        snapshot.setFallback(source.getFallback());
        snapshot.setMatrixCurrent(source.getMatrixCurrent());
        snapshot.setSampleSufficient(source.getSampleSufficient());
        snapshot.setRequirementCount(source.getRequirementCount());
        snapshot.setWarnings(source.getWarnings() == null ? List.of() : source.getWarnings());
        snapshot.setMissingRequirements(source.getMissingRequirements() == null ? List.of()
                : source.getMissingRequirements().stream()
                .filter(Objects::nonNull)
                .map(this::toMissingRequirementSnapshot)
                .toList());
        return snapshot;
    }

    private MissingRequirementSnapshot toMissingRequirementSnapshot(
            RequirementReadinessAgentContextVO.RequirementItemVO source) {
        MissingRequirementSnapshot snapshot = new MissingRequirementSnapshot();
        snapshot.setRequirementId(source.getRequirementId());
        snapshot.setRequirementKey(source.getRequirementKey());
        snapshot.setRequirementType(source.getRequirementType());
        snapshot.setRequirementName(source.getRequirementName());
        snapshot.setPriority(source.getPriority());
        snapshot.setCoverageLevel(source.getCoverageLevel());
        snapshot.setConfidenceLevel(source.getConfidenceLevel());
        snapshot.setFallback(source.getFallback());
        snapshot.setProjectEvidenceIds(source.getProjectEvidenceIds() == null
                ? List.of() : source.getProjectEvidenceIds());
        return snapshot;
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

    private List<PersonalKnowledgeReference> personalKnowledgeReferences(Long userId, JobCoachAgentContext context) {
        try {
            List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                            .eq(PersonalKnowledgeChunk::getUserId, userId)
                            .eq(PersonalKnowledgeChunk::getIndexStatus, "INDEXED")
                            .eq(PersonalKnowledgeChunk::getDeleted, 0)
                            .orderByDesc(PersonalKnowledgeChunk::getUpdatedAt)
                            .last("LIMIT " + MAX_PERSONAL_KNOWLEDGE_HINTS));
            if (chunks == null || chunks.isEmpty()) {
                return List.of();
            }
            Map<Long, PersonalKnowledgeDocument> documents = knowledgeDocuments(userId, chunks);
            return chunks.stream()
                    .filter(Objects::nonNull)
                    .map(chunk -> toPersonalKnowledgeReference(chunk, documents.get(chunk.getDocumentId())))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException ex) {
            log.info("Personal knowledge context unavailable userId={}, reason={}", userId, ex.getMessage());
            context.getContextWarnings().add("Personal knowledge hints are temporarily unavailable; skipped personal knowledge context.");
            return List.of();
        }
    }

    private Map<Long, PersonalKnowledgeDocument> knowledgeDocuments(Long userId, List<PersonalKnowledgeChunk> chunks) {
        List<Long> documentIds = chunks.stream()
                .map(PersonalKnowledgeChunk::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .in(PersonalKnowledgeDocument::getId, documentIds)
                        .eq(PersonalKnowledgeDocument::getDeleted, 0));
        Map<Long, PersonalKnowledgeDocument> result = new LinkedHashMap<>();
        if (documents != null) {
            documents.stream().filter(Objects::nonNull).forEach(document -> result.put(document.getId(), document));
        }
        return result;
    }

    private PersonalKnowledgeReference toPersonalKnowledgeReference(PersonalKnowledgeChunk chunk,
                                                                    PersonalKnowledgeDocument document) {
        if (chunk == null || chunk.getId() == null) {
            return null;
        }
        if (document != null && "DISABLED".equalsIgnoreCase(firstText(document.getStatus(), ""))) {
            return null;
        }
        PersonalKnowledgeReference reference = new PersonalKnowledgeReference();
        reference.setSourceType(SOURCE_KNOWLEDGE_CHUNK);
        reference.setSourceId(chunk.getId());
        reference.setSourceVersion(firstText(chunk.getChunkHash(), chunk.getNormalizationVersion(), ""));
        reference.setSourceTitle(firstText(chunk.getSourceRef(), document == null ? null : document.getTitle(),
                "Knowledge chunk " + chunk.getId()));
        reference.setConfidence(PERSONAL_KNOWLEDGE_CONTEXT_CONFIDENCE);
        reference.setSnapshotHash(TextFingerprintUtils.sha256Hex("knowledge-chunk:" + chunk.getId() + ":"
                + firstText(chunk.getChunkHash(), "") + ":" + firstText(String.valueOf(chunk.getUpdatedAt()), "")));
        return reference;
    }

    private String toPersonalKnowledgeHint(PersonalKnowledgeReference reference) {
        if (reference == null || reference.getSourceId() == null) {
            return null;
        }
        return truncate("knowledgeChunk#" + reference.getSourceId() + " "
                + firstText(reference.getSourceTitle(), "personal knowledge"), MAX_PERSONAL_KNOWLEDGE_HINT_LENGTH);
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

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
