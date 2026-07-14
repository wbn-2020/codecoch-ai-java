package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.ApplicationStatsAggregate;
import com.codecoachai.resume.domain.dto.ApplicationStatusCount;
import com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO;
import com.codecoachai.resume.domain.dto.JobApplicationSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeApplyAiSuggestionDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCopyDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCreateDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.ResumeSuggestionAdoption;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.vo.ApplicationCareerInsightSummaryVO;
import com.codecoachai.resume.domain.vo.ApplicationInsightItemVO;
import com.codecoachai.resume.domain.vo.ApplicationQualityVO;
import com.codecoachai.resume.domain.vo.ApplicationReminderCandidateVO;
import com.codecoachai.resume.domain.vo.CareerInsightItemVO;
import com.codecoachai.resume.domain.vo.JobApplicationAgentContextVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventVO;
import com.codecoachai.resume.domain.vo.JobApplicationStatsVO;
import com.codecoachai.resume.domain.vo.JobApplicationSummaryVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.domain.vo.ResumeVersionEffectItemVO;
import com.codecoachai.resume.domain.vo.ResumeVersionEffectVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionAdoptionVO;
import com.codecoachai.resume.domain.vo.ResumeVersionDiffVO;
import com.codecoachai.resume.domain.vo.ResumeVersionVO;
import com.codecoachai.resume.experimentv2.ExperimentV2ApplicationAutoAssignmentService;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeSuggestionAdoptionMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class V4ResumeCareerServiceImpl implements V4ResumeCareerService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "title", "realName", "email", "phone", "targetPosition", "skillStack",
            "workExperience", "educationExperience", "summary", "projects");
    private static final int VERSION_INSERT_MAX_ATTEMPTS = 5;
    private static final List<String> AGENT_APPLICATION_ACTIVE_STATUSES = List.of(
            "SAVED", "PREPARING", "APPLIED", "INTERVIEWING", "OFFER");
    private static final String AGENT_APPLICATION_ORDER_LIMIT_SQL =
            "ORDER BY next_follow_up_at IS NULL ASC, next_follow_up_at ASC, updated_at DESC LIMIT 20";

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper resumeProjectMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final ResumeJobMatchReportMapper resumeJobMatchReportMapper;
    private final ResumeSuggestionAdoptionMapper resumeSuggestionAdoptionMapper;
    private final JobApplicationEventMapper jobApplicationEventMapper;
    private final TargetJobMapper targetJobMapper;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;
    private final NotificationBusinessResolver notificationBusinessResolver;
    private final ResumeSearchSyncOutboxService resumeSearchSyncOutboxService;
    private final ExperimentV2ApplicationAutoAssignmentService experimentAutoAssignmentService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeVersionVO createVersion(Long resumeId, ResumeVersionCreateDTO dto) {
        Resume resume = ownedResume(resumeId);
        lockResume(resume);
        ResumeVersion version = insertVersionWithRetry(
                resume,
                dto == null ? null : dto.getVersionName(),
                StringUtils.hasText(dto == null ? null : dto.getSourceType()) ? dto.getSourceType() : "MANUAL",
                dto == null ? null : dto.getSourceId(),
                writeJson(snapshot(resume)),
                true);
        return toVersionVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeVersionVO copyVersion(Long resumeId, Long versionId, ResumeVersionCopyDTO dto) {
        Resume resume = ownedResume(resumeId);
        lockResume(resume);
        ResumeVersion source = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, source);

        String sourceName = StringUtils.hasText(source.getVersionName()) ? source.getVersionName() : "V" + source.getVersionNo();
        ResumeVersion copy = insertVersionWithRetry(
                resume,
                StringUtils.hasText(dto == null ? null : dto.getVersionName())
                        ? dto.getVersionName()
                        : "Copy of " + sourceName,
                "COPY",
                source.getId(),
                StringUtils.hasText(source.getSnapshotJson()) ? source.getSnapshotJson() : writeJson(snapshot(resume)),
                false);
        return toVersionVO(copy);
    }

    @Override
    public List<ResumeVersionVO> listVersions(Long resumeId) {
        ownedResume(resumeId);
        Long userId = currentUserId();
        return resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getUserId, userId)
                        .eq(ResumeVersion::getResumeId, resumeId)
                        .orderByDesc(ResumeVersion::getVersionNo))
                .stream().map(this::toVersionVO).toList();
    }

    @Override
    public ResumeVersionVO getVersion(Long versionId) {
        return toVersionVO(ownedVersion(versionId));
    }

    @Override
    public ResumeVersionDiffVO diffVersion(Long resumeId, Long versionId) {
        Resume resume = ownedResume(resumeId);
        ResumeVersion version = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, version);
        return buildDiff(resumeId, null, versionId, "CURRENT_RESUME", "VERSION",
                snapshot(resume), readMap(version.getSnapshotJson()));
    }

    @Override
    public ResumeVersionDiffVO diffVersions(Long sourceVersionId, Long targetVersionId) {
        ResumeVersion source = ownedVersion(sourceVersionId);
        ResumeVersion target = ownedVersion(targetVersionId);
        if (!Objects.equals(source.getResumeId(), target.getResumeId())) {
            throw new IllegalArgumentException("只能对同一份简历的版本进行比较");
        }
        return buildDiff(source.getResumeId(), sourceVersionId, targetVersionId, "SOURCE_VERSION", "TARGET_VERSION",
                readMap(source.getSnapshotJson()), readMap(target.getSnapshotJson()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeVersionVO rollbackVersion(Long resumeId, Long versionId) {
        Resume resume = ownedResume(resumeId);
        lockResume(resume);
        ResumeVersion version = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, version);
        Map<String, Object> versionSnapshot = readMap(version.getSnapshotJson());
        applySnapshot(resume, versionSnapshot);
        resumeMapper.updateById(resume);
        restoreProjects(resume.getId(), versionSnapshot);
        clearCurrentVersions(resumeId, resume.getUserId());
        version.setCurrentFlag(1);
        resumeVersionMapper.updateById(version);
        resumeSearchSyncOutboxService.enqueue(resume.getId(), resume.getUserId(),
                ResumeSearchSyncOutboxService.OP_UPSERT);
        return toVersionVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSuggestionAdoptionVO applyAiSuggestion(Long versionId, ResumeApplyAiSuggestionDTO dto) {
        ResumeVersion version = ownedVersion(versionId);
        Resume resume = ownedResume(version.getResumeId());
        lockResume(resume);
        Map<String, Object> versionSnapshot = readMap(version.getSnapshotJson());
        applySnapshot(resume, versionSnapshot);
        resumeMapper.updateById(resume);
        restoreProjects(resume.getId(), versionSnapshot);
        clearCurrentVersions(version.getResumeId(), resume.getUserId());
        version.setCurrentFlag(1);
        resumeVersionMapper.updateById(version);

        ResumeSuggestionAdoption adoption = new ResumeSuggestionAdoption();
        adoption.setUserId(resume.getUserId());
        adoption.setResumeId(resume.getId());
        adoption.setOptimizeRecordId(resolveOptimizeRecordId(version, dto));
        adoption.setResumeVersionId(version.getId());
        adoption.setSuggestionType(StringUtils.hasText(dto == null ? null : dto.getSuggestionType())
                ? dto.getSuggestionType()
                : "AI_RESUME_VERSION");
        adoption.setStatus(StringUtils.hasText(dto == null ? null : dto.getStatus()) ? dto.getStatus() : "ADOPTED");
        adoption.setNote(dto == null ? null : dto.getNote());
        resumeSuggestionAdoptionMapper.insert(adoption);
        resumeSearchSyncOutboxService.enqueue(resume.getId(), resume.getUserId(),
                ResumeSearchSyncOutboxService.OP_UPSERT);
        return toSuggestionAdoptionVO(adoption);
    }

    @Override
    public List<JobApplicationVO> listApplications(String status) {
        return listApplications(status, null, null, null);
    }

    @Override
    public List<JobApplicationVO> listApplications(String status, Integer page, Integer size, String keyword) {
        Long userId = currentUserId();
        LambdaQueryWrapper<JobApplication> query = new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .eq(StringUtils.hasText(status), JobApplication::getStatus, normalizeApplicationStatus(status))
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(JobApplication::getCompanyName, keyword)
                        .or()
                        .like(JobApplication::getJobTitle, keyword)
                        .or()
                        .like(JobApplication::getSource, keyword)
                        .or()
                        .like(JobApplication::getNote, keyword))
                .orderByDesc(JobApplication::getUpdatedAt)
                .orderByDesc(JobApplication::getId);
        if (page != null || size != null) {
            int effectivePage = page == null || page < 1 ? 1 : page;
            int effectiveSize = size == null || size < 1 ? 20 : Math.min(size, 100);
            long offset = (long) (effectivePage - 1) * effectiveSize;
            query.last("LIMIT " + effectiveSize + " OFFSET " + offset);
        }
        List<JobApplication> applications = jobApplicationMapper.selectList(query);
        return toApplicationVOList(applications);
    }

    @Override
    public JobApplicationStatsVO getApplicationStats(LocalDateTime now) {
        Long userId = currentUserId();
        LocalDateTime generatedAt = now == null ? LocalDateTime.now() : now;
        ApplicationStatsAggregate aggregate = jobApplicationMapper.selectStats(
                userId,
                generatedAt,
                generatedAt.toLocalDate().atStartOfDay(),
                generatedAt.toLocalDate().plusDays(1).atStartOfDay(),
                generatedAt.minusDays(14));
        List<ApplicationStatusCount> statusCounts = jobApplicationMapper.selectStatusCounts(userId);
        if (aggregate == null) {
            List<JobApplication> applications = jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                    .eq(JobApplication::getUserId, userId)
                    .eq(JobApplication::getDeleted, CommonConstants.NO));
            return buildApplicationStats(applications, generatedAt);
        }
        return toApplicationStats(aggregate, statusCounts, generatedAt);
    }

    @Override
    public List<JobApplicationAgentContextVO> listAgentApplicationContextForUser(Long userId, Long targetJobId,
                                                                                 LocalDateTime now) {
        if (userId == null) {
            return List.of();
        }
        List<JobApplication> applications = jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .eq(targetJobId != null, JobApplication::getTargetJobId, targetJobId)
                .in(JobApplication::getStatus, AGENT_APPLICATION_ACTIVE_STATUSES)
                .last(AGENT_APPLICATION_ORDER_LIMIT_SQL));
        return toAgentApplicationContextVOList(applications, now);
    }

    @Override
    public List<ApplicationReminderCandidateVO> listApplicationReminderCandidates(Long userId, LocalDate date,
                                                                                 LocalDateTime now) {
        if (userId == null) {
            return List.of();
        }
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        LocalDate reminderDate = date == null ? effectiveNow.toLocalDate() : date;
        List<JobApplication> applications = jobApplicationMapper.selectReminderCandidates(
                userId,
                effectiveNow,
                reminderDate.atStartOfDay(),
                reminderDate.plusDays(1).atStartOfDay(),
                5);
        if (applications == null) {
            applications = jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                    .eq(JobApplication::getUserId, userId)
                    .eq(JobApplication::getDeleted, CommonConstants.NO)
                    .isNotNull(JobApplication::getNextFollowUpAt));
        }
        if (applications == null || applications.isEmpty()) {
            return List.of();
        }
        return applications.stream()
                .filter(app -> isReminderCandidate(app, reminderDate, effectiveNow))
                .sorted(Comparator
                        .comparing((JobApplication app) -> !isApplicationFollowUpOverdue(app, effectiveNow))
                        .thenComparing(JobApplication::getNextFollowUpAt)
                        .thenComparing(JobApplication::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(app -> toApplicationReminderCandidateVO(app, reminderDate, effectiveNow))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationVO createApplication(JobApplicationSaveDTO dto) {
        Long userId = currentUserId();
        JobApplicationSaveDTO request = prepareApplicationRequest(dto, userId);
        JobApplication existing = findApplicationByMatchReport(request.getMatchReportId(), userId);
        if (existing != null) {
            return toApplicationVOWithDetails(existing);
        }
        JobApplication app = new JobApplication();
        app.setUserId(userId);
        fillApplication(app, request);
        jobApplicationMapper.insert(app);
        autoAssignExperimentAfterCommit(app);
        return toApplicationVOWithDetails(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationVO updateApplication(Long id, JobApplicationSaveDTO dto) {
        JobApplication app = ownedApplication(id);
        JobApplicationSaveDTO request = prepareApplicationRequest(dto, app.getUserId());
        ensureMatchReportNotLinkedToAnotherApplication(request.getMatchReportId(), app.getUserId(), app.getId());
        fillApplication(app, request);
        jobApplicationMapper.updateById(app);
        return toApplicationVOWithDetails(jobApplicationMapper.selectById(id));
    }

    @Override
    public List<JobApplicationEventVO> listApplicationEvents(Long applicationId) {
        JobApplication app = ownedApplication(applicationId);
        return jobApplicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                        .eq(JobApplicationEvent::getUserId, app.getUserId())
                        .eq(JobApplicationEvent::getApplicationId, applicationId)
                        .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                        .orderByDesc(JobApplicationEvent::getEventTime)
                        .orderByDesc(JobApplicationEvent::getCreatedAt))
                .stream().map(this::toApplicationEventVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationEventVO createApplicationEvent(Long applicationId, JobApplicationEventSaveDTO dto) {
        JobApplication app = ownedApplication(applicationId);
        return createApplicationEvent(app, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationEventVO createApplicationEventForUser(Long userId, Long applicationId,
                                                               JobApplicationEventSaveDTO dto) {
        JobApplication app = applicationForUser(userId, applicationId);
        return createApplicationEvent(app, dto);
    }

    private JobApplicationEventVO createApplicationEvent(JobApplication app, JobApplicationEventSaveDTO dto) {
        JobApplicationEvent existing = findExistingInterviewCompletedEvent(app, dto);
        if (existing != null) {
            return toApplicationEventVO(existing);
        }
        JobApplicationEvent event = new JobApplicationEvent();
        event.setUserId(app.getUserId());
        event.setApplicationId(app.getId());
        event.setEventType(StringUtils.hasText(dto == null ? null : dto.getEventType()) ? dto.getEventType() : "NOTE");
        event.setEventTime(dto == null || dto.getEventTime() == null ? LocalDateTime.now() : dto.getEventTime());
        event.setSummary(dto == null ? null : dto.getSummary());
        event.setReviewJson(writeReviewJson(dto));
        jobApplicationEventMapper.insert(event);
        syncApplicationStatusFromEvent(app, event);
        if (isAgentFollowUpCompletionEvent(event.getEventType())) {
            completeAgentFollowUpAfterCommit(app.getUserId(), app.getId(), event.getId());
        }
        return toApplicationEventVO(event);
    }

    @Override
    public JobApplicationSummaryVO getApplicationSummaryForUser(Long userId, Long applicationId) {
        if (userId == null || applicationId == null) {
            return null;
        }
        JobApplication app = jobApplicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        return toApplicationSummaryVO(app);
    }

    @Override
    public ApplicationCareerInsightSummaryVO getApplicationCareerInsightSummaryForUser(Long userId, Integer days,
                                                                                      LocalDateTime now) {
        LocalDateTime generatedAt = now == null ? LocalDateTime.now() : now;
        int rangeDays = normalizeInsightRangeDays(days);
        ApplicationCareerInsightSummaryVO summary = new ApplicationCareerInsightSummaryVO();
        summary.setRangeDays(rangeDays);
        summary.setGeneratedAt(generatedAt);
        if (userId == null) {
            return summary;
        }

        LocalDateTime startAt = generatedAt.toLocalDate().minusDays(rangeDays - 1L).atStartOfDay();
        List<JobApplication> applications = jobApplicationMapper.selectInsightRange(userId, startAt, generatedAt);
        List<JobApplication> scopedApplications;
        if (applications == null) {
            List<JobApplication> fallback = jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                    .eq(JobApplication::getUserId, userId)
                    .eq(JobApplication::getDeleted, CommonConstants.NO)
                    .orderByDesc(JobApplication::getAppliedAt)
                    .orderByDesc(JobApplication::getCreatedAt)
                    .orderByDesc(JobApplication::getUpdatedAt));
            scopedApplications = (fallback == null ? List.<JobApplication>of() : fallback).stream()
                    .filter(app -> isApplicationInInsightRange(app, startAt, generatedAt))
                    .toList();
        } else {
            scopedApplications = applications;
        }
        if (scopedApplications.isEmpty()) {
            summary.setQuality(buildApplicationQuality(summary, Map.of(), generatedAt));
            summary.setResumeVersionEffect(buildResumeVersionEffect(scopedApplications, Map.of(), Map.of()));
            return summary;
        }

        Map<Long, List<JobApplicationEvent>> eventsByApplicationId = applicationEventsByApplicationId(scopedApplications);
        Map<Long, ResumeVersion> baseVersionMap = resumeVersionMap(scopedApplications);
        Map<Long, ResumeVersion> versionMap = includeCurrentResumeVersions(baseVersionMap);
        List<ApplicationCareerFacts> facts = scopedApplications.stream()
                .map(app -> buildCareerFacts(app,
                        app.getId() == null ? List.of() : eventsByApplicationId.getOrDefault(app.getId(), List.of()),
                        app.getResumeVersionId() == null ? null : versionMap.get(app.getResumeVersionId()),
                        generatedAt))
                .toList();

        summary.setApplicationCount((long) facts.size());
        summary.setFollowedUpApplicationCount(facts.stream().filter(ApplicationCareerFacts::hasFollowUp).count());
        summary.setInterviewApplicationCount(facts.stream().filter(ApplicationCareerFacts::hasInterview).count());
        summary.setOfferApplicationCount(facts.stream().filter(ApplicationCareerFacts::hasOffer).count());
        summary.setRejectedOrClosedApplicationCount(facts.stream().filter(ApplicationCareerFacts::terminal).count());
        summary.setApplications(facts.stream().map(ApplicationCareerFacts::item).toList());
        Map<Long, ApplicationCareerFacts> factsByApplicationId = facts.stream()
                .filter(fact -> fact.application() != null && fact.application().getId() != null)
                .collect(Collectors.toMap(fact -> fact.application().getId(), fact -> fact, (left, right) -> left));
        summary.setQuality(buildApplicationQuality(summary, factsByApplicationId, generatedAt));
        summary.setResumeVersionEffect(buildResumeVersionEffect(scopedApplications, factsByApplicationId, versionMap));
        return summary;
    }

    private void completeAgentFollowUpAfterCommit(Long userId, Long applicationId, Long eventId) {
        if (userId == null || applicationId == null || eventId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentBusinessActionNotifier.completeApplicationFollowUp(userId, applicationId, eventId);
                    notificationBusinessResolver.resolveApplicationFollowUp(userId, applicationId,
                            "JOB_APPLICATION_EVENT:" + eventId);
                }
            });
            return;
        }
        agentBusinessActionNotifier.completeApplicationFollowUp(userId, applicationId, eventId);
        notificationBusinessResolver.resolveApplicationFollowUp(userId, applicationId,
                "JOB_APPLICATION_EVENT:" + eventId);
    }

    private void autoAssignExperimentAfterCommit(JobApplication application) {
        Runnable action = () -> {
            try {
                experimentAutoAssignmentService.autoAssign(application);
            } catch (Exception exception) {
                log.error("Experiment auto-assignment failed after application creation: applicationId={}, userId={}",
                        application == null ? null : application.getId(),
                        application == null ? null : application.getUserId(),
                        exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private JobApplicationStatsVO buildApplicationStats(List<JobApplication> applications, LocalDateTime now) {
        JobApplicationStatsVO vo = new JobApplicationStatsVO();
        vo.setGeneratedAt(now);
        if (applications == null || applications.isEmpty()) {
            return vo;
        }

        LocalDateTime staleBefore = now.minusDays(14);
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (JobApplication app : applications) {
            vo.setTotal(vo.getTotal() + 1);
            String status = normalizeApplicationStatus(app.getStatus());
            if (StringUtils.hasText(status)) {
                statusCounts.merge(status, 1L, Long::sum);
            }
            if ("INTERVIEWING".equals(status)) {
                vo.setInterviewCount(vo.getInterviewCount() + 1);
            } else if ("OFFER".equals(status)) {
                vo.setOfferCount(vo.getOfferCount() + 1);
            } else if ("REJECTED".equals(status)) {
                vo.setRejectedCount(vo.getRejectedCount() + 1);
            } else if ("CLOSED".equals(status)) {
                vo.setClosedCount(vo.getClosedCount() + 1);
            }

            if (!AGENT_APPLICATION_ACTIVE_STATUSES.contains(status)) {
                continue;
            }

            vo.setActiveCount(vo.getActiveCount() + 1);
            LocalDateTime nextFollowUpAt = app.getNextFollowUpAt();
            if (nextFollowUpAt == null) {
                vo.setNoFollowUpCount(vo.getNoFollowUpCount() + 1);
            } else if (nextFollowUpAt.isBefore(now)) {
                vo.setOverdueFollowUpCount(vo.getOverdueFollowUpCount() + 1);
            } else if (nextFollowUpAt.toLocalDate().equals(now.toLocalDate())) {
                vo.setDueTodayFollowUpCount(vo.getDueTodayFollowUpCount() + 1);
            }

            LocalDateTime updatedAt = app.getUpdatedAt();
            if (updatedAt != null && updatedAt.isBefore(staleBefore)) {
                vo.setStaleActiveCount(vo.getStaleActiveCount() + 1);
            }
        }
        vo.setStatusCounts(statusCounts);
        return vo;
    }

    private JobApplicationStatsVO toApplicationStats(ApplicationStatsAggregate aggregate,
                                                      List<ApplicationStatusCount> statusCounts,
                                                      LocalDateTime generatedAt) {
        JobApplicationStatsVO vo = new JobApplicationStatsVO();
        vo.setGeneratedAt(generatedAt);
        vo.setTotal(zeroIfNull(aggregate.getTotal()));
        vo.setActiveCount(zeroIfNull(aggregate.getActiveCount()));
        vo.setOverdueFollowUpCount(zeroIfNull(aggregate.getOverdueFollowUpCount()));
        vo.setDueTodayFollowUpCount(zeroIfNull(aggregate.getDueTodayFollowUpCount()));
        vo.setNoFollowUpCount(zeroIfNull(aggregate.getNoFollowUpCount()));
        vo.setStaleActiveCount(zeroIfNull(aggregate.getStaleActiveCount()));
        vo.setInterviewCount(zeroIfNull(aggregate.getInterviewCount()));
        vo.setOfferCount(zeroIfNull(aggregate.getOfferCount()));
        vo.setRejectedCount(zeroIfNull(aggregate.getRejectedCount()));
        vo.setClosedCount(zeroIfNull(aggregate.getClosedCount()));
        Map<String, Long> counts = new LinkedHashMap<>();
        if (statusCounts != null) {
            statusCounts.stream()
                    .filter(item -> item != null && StringUtils.hasText(item.getStatus()))
                    .forEach(item -> counts.put(item.getStatus(), zeroIfNull(item.getCount())));
        }
        vo.setStatusCounts(counts);
        return vo;
    }

    private long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private ApplicationQualityVO buildApplicationQuality(ApplicationCareerInsightSummaryVO summary,
                                                        Map<Long, ApplicationCareerFacts> factsByApplicationId,
                                                        LocalDateTime now) {
        ApplicationQualityVO quality = new ApplicationQualityVO();
        List<ApplicationCareerFacts> facts = factsByApplicationId == null
                ? List.of()
                : List.copyOf(factsByApplicationId.values());
        long total = summary == null || summary.getApplicationCount() == null ? facts.size() : summary.getApplicationCount();
        quality.setTotalApplications(total);
        quality.setWithResumeVersionCount(facts.stream()
                .filter(fact -> fact.application() != null && fact.application().getResumeVersionId() != null)
                .count());
        quality.setWithFollowUpCount(facts.stream().filter(ApplicationCareerFacts::hasFollowUp).count());
        quality.setOverdueFollowUpCount(facts.stream().filter(ApplicationCareerFacts::overdueFollowUp).count());
        quality.setStaleApplicationCount(facts.stream().filter(ApplicationCareerFacts::stale).count());
        quality.setNoEventApplicationCount(facts.stream().filter(ApplicationCareerFacts::noEvent).count());
        quality.setResumeVersionCoverageRate(rate(quality.getWithResumeVersionCount(), total));
        quality.setFollowUpCoverageRate(rate(quality.getWithFollowUpCount(), total));
        if (total > 0 && total < 3) {
            quality.getWarnings().add(careerWarning("LOW_SAMPLE", "样本不足",
                    "投递样本较少，当前结果适合观察趋势，暂不做强结论。",
                    "INFO", total + " applications", "继续记录投递", "/applications"));
        }
        if (quality.getOverdueFollowUpCount() > 0) {
            quality.getWarnings().add(careerWarning("OVERDUE_FOLLOW_UP", "存在逾期跟进",
                    "有投递已经超过计划跟进时间，建议优先补跟进。",
                    "HIGH", quality.getOverdueFollowUpCount() + " overdue", "查看逾期跟进",
                    "/applications?followUp=overdue"));
        }
        if (quality.getStaleApplicationCount() > 0) {
            quality.getWarnings().add(careerWarning("STALE_APPLICATION", "存在长期无进展投递",
                    "部分未结束投递超过 7 天没有新事件或更新时间。",
                    "MEDIUM", quality.getStaleApplicationCount() + " stale", "查看投递工作台", "/applications"));
        }
        if (quality.getNoEventApplicationCount() > 0) {
            quality.getWarnings().add(careerWarning("NO_EVENT", "存在无事件投递",
                    "部分投递还没有记录跟进、面试或结果事件。",
                    "LOW", quality.getNoEventApplicationCount() + " no events", "补充投递事件", "/applications"));
        }
        if (total > 0 && quality.getWithResumeVersionCount() < total) {
            quality.getWarnings().add(careerWarning("MISSING_RESUME_VERSION", "存在未绑定版本投递",
                    "绑定简历版本后才能复盘版本使用效果。",
                    "LOW", (total - quality.getWithResumeVersionCount()) + " without version",
                    "整理简历版本", "/applications"));
        }
        return quality;
    }

    private ResumeVersionEffectVO buildResumeVersionEffect(List<JobApplication> applications,
                                                          Map<Long, ApplicationCareerFacts> factsByApplicationId,
                                                          Map<Long, ResumeVersion> versionMap) {
        ResumeVersionEffectVO effect = new ResumeVersionEffectVO();
        List<JobApplication> scopedApplications = applications == null ? List.of() : applications;
        effect.setVersionUsedCount(scopedApplications.stream()
                .filter(app -> app != null && app.getResumeVersionId() != null)
                .count());
        effect.setApplicationsWithoutVersionCount(scopedApplications.stream()
                .filter(app -> app != null && app.getResumeVersionId() == null)
                .count());
        Map<Long, List<JobApplication>> applicationsByVersion = scopedApplications.stream()
                .filter(app -> app != null && app.getResumeVersionId() != null)
                .collect(Collectors.groupingBy(JobApplication::getResumeVersionId, LinkedHashMap::new, Collectors.toList()));
        long maxApplicationCount = applicationsByVersion.values().stream()
                .mapToLong(List::size)
                .max()
                .orElse(0L);
        Map<Long, ResumeVersion> safeVersionMap = versionMap == null ? Map.of() : versionMap;
        List<ResumeVersionEffectItemVO> items = new ArrayList<>(applicationsByVersion.entrySet().stream()
                .map(entry -> buildResumeVersionEffectItem(entry.getKey(), entry.getValue(),
                        factsByApplicationId == null ? Map.of() : factsByApplicationId,
                        safeVersionMap,
                        maxApplicationCount))
                .toList());
        safeVersionMap.values().stream()
                .filter(version -> version != null && version.getId() != null)
                .filter(version -> Objects.equals(version.getCurrentFlag(), 1))
                .filter(version -> !applicationsByVersion.containsKey(version.getId()))
                .map(version -> buildResumeVersionEffectItem(version.getId(), List.of(),
                        factsByApplicationId == null ? Map.of() : factsByApplicationId,
                        safeVersionMap,
                        maxApplicationCount))
                .forEach(items::add);
        items = items.stream()
                .sorted(Comparator
                        .comparing(ResumeVersionEffectItemVO::getApplicationCount, Comparator.reverseOrder())
                        .thenComparing(ResumeVersionEffectItemVO::getCurrentFlag,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ResumeVersionEffectItemVO::getResumeVersionId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        effect.setVersions(items);
        effect.setCurrentVersionApplicationCount(items.stream()
                .filter(item -> Objects.equals(item.getCurrentFlag(), 1))
                .mapToLong(item -> item.getApplicationCount() == null ? 0L : item.getApplicationCount())
                .sum());
        return effect;
    }

    private ResumeVersionEffectItemVO buildResumeVersionEffectItem(Long resumeVersionId,
                                                                  List<JobApplication> applications,
                                                                  Map<Long, ApplicationCareerFacts> factsByApplicationId,
                                                                  Map<Long, ResumeVersion> versionMap,
                                                                  long maxApplicationCount) {
        ResumeVersion version = versionMap.get(resumeVersionId);
        List<JobApplication> versionApplications = applications == null ? List.of() : applications;
        long applicationCount = versionApplications.size();
        long interviewCount = versionApplications.stream()
                .map(JobApplication::getId)
                .map(factsByApplicationId::get)
                .filter(Objects::nonNull)
                .filter(ApplicationCareerFacts::hasInterview)
                .count();
        long offerCount = versionApplications.stream()
                .map(JobApplication::getId)
                .map(factsByApplicationId::get)
                .filter(Objects::nonNull)
                .filter(ApplicationCareerFacts::hasOffer)
                .count();
        ResumeVersionEffectItemVO item = new ResumeVersionEffectItemVO();
        item.setResumeVersionId(resumeVersionId);
        item.setApplicationCount(applicationCount);
        item.setInterviewCount(interviewCount);
        item.setOfferCount(offerCount);
        if (version != null) {
            item.setResumeId(version.getResumeId());
            item.setVersionNo(version.getVersionNo());
            item.setVersionName(version.getVersionName());
            item.setCurrentFlag(version.getCurrentFlag());
        }
        item.setSampleLevel(applicationCount < 3 ? "LOW" : "ENOUGH");
        item.setInsightLabel(resumeVersionInsightLabel(applicationCount, interviewCount, offerCount, maxApplicationCount));
        return item;
    }

    private String resumeVersionInsightLabel(long applicationCount, long interviewCount, long offerCount,
                                             long maxApplicationCount) {
        if (applicationCount < 3) {
            return "样本不足";
        }
        if (applicationCount == maxApplicationCount) {
            return "使用最多";
        }
        if (offerCount > 0) {
            return "获得 Offer";
        }
        if (interviewCount > 0) {
            return "已进入面试";
        }
        return "继续观察";
    }

    private Map<Long, List<JobApplicationEvent>> applicationEventsByApplicationId(List<JobApplication> applications) {
        Set<Long> applicationIds = applications == null
                ? Set.of()
                : applications.stream()
                .map(JobApplication::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        List<JobApplicationEvent> events = jobApplicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                .in(JobApplicationEvent::getApplicationId, applicationIds)
                .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                .orderByDesc(JobApplicationEvent::getEventTime)
                .orderByDesc(JobApplicationEvent::getCreatedAt)
                .orderByDesc(JobApplicationEvent::getId));
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        return events.stream()
                .filter(event -> event != null && event.getApplicationId() != null)
                .collect(Collectors.groupingBy(JobApplicationEvent::getApplicationId, LinkedHashMap::new, Collectors.toList()));
    }

    private ApplicationCareerFacts buildCareerFacts(JobApplication app, List<JobApplicationEvent> events,
                                                   ResumeVersion version, LocalDateTime now) {
        List<JobApplicationEvent> applicationEvents = events == null ? List.of() : events;
        JobApplicationEvent latestEvent = latestEvent(applicationEvents);
        boolean hasFollowUpEvent = applicationEvents.stream().anyMatch(this::isFollowUpEvent);
        boolean hasFollowUp = (app != null && app.getNextFollowUpAt() != null) || hasFollowUpEvent;
        boolean hasInterview = isInterviewStatus(app == null ? null : app.getStatus())
                || applicationEvents.stream().anyMatch(this::isInterviewEvent);
        boolean hasOffer = isOfferStatus(app == null ? null : app.getStatus())
                || applicationEvents.stream().anyMatch(this::isOfferEvent);
        boolean terminal = isTerminalStatus(app == null ? null : app.getStatus())
                || applicationEvents.stream().anyMatch(this::isTerminalEvent);
        boolean overdueFollowUp = app != null && app.getNextFollowUpAt() != null
                && app.getNextFollowUpAt().isBefore(now)
                && !terminal;
        boolean noEvent = applicationEvents.isEmpty();
        boolean stale = isStaleApplication(app, latestEvent, terminal, now);
        ApplicationInsightItemVO item = toApplicationInsightItem(app, version, latestEvent,
                hasFollowUp, hasInterview, hasOffer, terminal);
        return new ApplicationCareerFacts(app, item, hasFollowUpEvent, hasFollowUp, hasInterview, hasOffer,
                terminal, overdueFollowUp, stale, noEvent);
    }

    private ApplicationInsightItemVO toApplicationInsightItem(JobApplication app, ResumeVersion version,
                                                             JobApplicationEvent latestEvent, boolean hasFollowUp,
                                                             boolean hasInterview, boolean hasOffer,
                                                             boolean terminal) {
        ApplicationInsightItemVO item = new ApplicationInsightItemVO();
        if (app == null) {
            return item;
        }
        item.setApplicationId(app.getId());
        item.setResumeVersionId(app.getResumeVersionId());
        item.setCompanyName(app.getCompanyName());
        item.setJobTitle(app.getJobTitle());
        item.setStatus(normalizeApplicationStatus(app.getStatus()));
        item.setAppliedAt(app.getAppliedAt());
        item.setNextFollowUpAt(app.getNextFollowUpAt());
        item.setHasFollowUp(hasFollowUp);
        item.setHasInterview(hasInterview);
        item.setHasOffer(hasOffer);
        item.setTerminal(terminal);
        if (version != null) {
            item.setResumeId(version.getResumeId());
            item.setResumeVersionNo(version.getVersionNo());
            item.setResumeVersionName(version.getVersionName());
            item.setResumeVersionCurrentFlag(version.getCurrentFlag());
        }
        if (latestEvent != null) {
            item.setLatestEventId(latestEvent.getId());
            item.setLatestEventType(latestEvent.getEventType());
            item.setLatestEventTime(latestEvent.getEventTime());
            item.setLatestEventSummary(latestEvent.getSummary());
        }
        return item;
    }

    private JobApplicationEvent latestEvent(List<JobApplicationEvent> events) {
        return (events == null ? List.<JobApplicationEvent>of() : events).stream()
                .filter(Objects::nonNull)
                .max(Comparator
                        .comparing(JobApplicationEvent::getEventTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(JobApplicationEvent::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(JobApplicationEvent::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean isApplicationInInsightRange(JobApplication app, LocalDateTime startAt, LocalDateTime endAt) {
        if (app == null) {
            return false;
        }
        LocalDateTime businessTime = app.getAppliedAt() != null ? app.getAppliedAt() : app.getCreatedAt();
        if (businessTime == null) {
            businessTime = app.getUpdatedAt();
        }
        if (businessTime == null || startAt == null || endAt == null) {
            return true;
        }
        return !businessTime.isBefore(startAt) && !businessTime.isAfter(endAt);
    }

    private boolean isStaleApplication(JobApplication app, JobApplicationEvent latestEvent,
                                       boolean terminal, LocalDateTime now) {
        if (app == null || terminal || !AGENT_APPLICATION_ACTIVE_STATUSES.contains(normalizeApplicationStatus(app.getStatus()))) {
            return false;
        }
        LocalDateTime staleBefore = now.minusDays(7);
        LocalDateTime referenceTime = latestEvent == null ? null : latestEvent.getEventTime();
        if (referenceTime == null && latestEvent != null) {
            referenceTime = latestEvent.getCreatedAt();
        }
        if (referenceTime == null) {
            referenceTime = app.getUpdatedAt();
        }
        if (referenceTime == null) {
            referenceTime = app.getAppliedAt();
        }
        if (referenceTime == null) {
            referenceTime = app.getCreatedAt();
        }
        return referenceTime != null && referenceTime.isBefore(staleBefore);
    }

    private boolean isFollowUpEvent(JobApplicationEvent event) {
        String normalized = normalizeApplicationStatus(event == null ? null : event.getEventType());
        return "FOLLOW_UP".equals(normalized) || normalized != null && normalized.startsWith("FOLLOW_UP_");
    }

    private boolean isInterviewEvent(JobApplicationEvent event) {
        return isInterviewStatus(statusFromEventType(event == null ? null : event.getEventType()));
    }

    private boolean isOfferEvent(JobApplicationEvent event) {
        return isOfferStatus(statusFromEventType(event == null ? null : event.getEventType()));
    }

    private boolean isTerminalEvent(JobApplicationEvent event) {
        return isTerminalStatus(statusFromEventType(event == null ? null : event.getEventType()));
    }

    private boolean isInterviewStatus(String status) {
        return "INTERVIEWING".equals(normalizeApplicationStatus(status));
    }

    private boolean isOfferStatus(String status) {
        return "OFFER".equals(normalizeApplicationStatus(status));
    }

    private boolean isTerminalStatus(String status) {
        String normalized = normalizeApplicationStatus(status);
        return "REJECTED".equals(normalized) || "CLOSED".equals(normalized);
    }

    private int normalizeInsightRangeDays(Integer days) {
        if (days == null) {
            return 30;
        }
        if (days <= 7) {
            return 7;
        }
        if (days <= 30) {
            return 30;
        }
        return 90;
    }

    private Double rate(Long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        long safeNumerator = numerator == null ? 0L : numerator;
        return (double) safeNumerator / denominator;
    }

    private CareerInsightItemVO careerWarning(String type, String title, String description, String severity,
                                              String evidence, String actionLabel, String actionPath) {
        CareerInsightItemVO item = new CareerInsightItemVO();
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setSeverity(severity);
        item.setEvidence(evidence);
        item.setActionLabel(actionLabel);
        item.setActionPath(actionPath);
        return item;
    }

    private record ApplicationCareerFacts(
            JobApplication application,
            ApplicationInsightItemVO item,
            boolean hasFollowUpEvent,
            boolean hasFollowUp,
            boolean hasInterview,
            boolean hasOffer,
            boolean terminal,
            boolean overdueFollowUp,
            boolean stale,
            boolean noEvent) {
    }

    private Resume ownedResume(Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        Long userId = currentUserId();
        if (resume == null || !Objects.equals(userId, resume.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历不存在或无权访问");
        }
        return resume;
    }

    private ResumeVersion ownedVersion(Long versionId) {
        ResumeVersion version = resumeVersionMapper.selectById(versionId);
        Long userId = currentUserId();
        if (version == null || !Objects.equals(userId, version.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历版本不存在或无权访问");
        }
        return version;
    }

    private JobApplication ownedApplication(Long applicationId) {
        return applicationForUser(currentUserId(), applicationId);
    }

    private JobApplication applicationForUser(Long userId, Long applicationId) {
        JobApplication app = jobApplicationMapper.selectById(applicationId);
        if (app == null || !Objects.equals(userId, app.getUserId())
                || Objects.equals(app.getDeleted(), CommonConstants.YES)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "投递记录不存在或无权访问");
        }
        return app;
    }

    private ResumeJobMatchReport ownedMatchReport(Long matchReportId, Long userId) {
        ResumeJobMatchReport report = resumeJobMatchReportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getId, matchReportId)
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "匹配报告不存在或无权访问");
        }
        return report;
    }

    private JobApplication findApplicationByMatchReport(Long matchReportId, Long userId) {
        if (matchReportId == null || userId == null) {
            return null;
        }
        return jobApplicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getMatchReportId, matchReportId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .orderByDesc(JobApplication::getUpdatedAt)
                .last("limit 1"));
    }

    private void ensureMatchReportNotLinkedToAnotherApplication(Long matchReportId, Long userId, Long applicationId) {
        JobApplication linked = findApplicationByMatchReport(matchReportId, userId);
        if (linked != null && !Objects.equals(linked.getId(), applicationId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该匹配报告已关联其他投递进度");
        }
    }

    private JobApplicationSaveDTO prepareApplicationRequest(JobApplicationSaveDTO dto, Long userId) {
        JobApplicationSaveDTO request = dto == null ? new JobApplicationSaveDTO() : dto;
        ResumeJobMatchReport report = request.getMatchReportId() == null
                ? null
                : ownedMatchReport(request.getMatchReportId(), userId);
        if (report != null) {
            if (request.getTargetJobId() == null) {
                request.setTargetJobId(report.getTargetJobId());
            } else if (report.getTargetJobId() != null && !Objects.equals(request.getTargetJobId(), report.getTargetJobId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "投递岗位与匹配报告岗位不一致");
            }
            if (request.getResumeVersionId() == null) {
                request.setResumeVersionId(report.getResumeVersionId());
            } else if (report.getResumeVersionId() != null
                    && !Objects.equals(request.getResumeVersionId(), report.getResumeVersionId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "投递简历版本与匹配报告版本不一致");
            }
        }
        if (request.getResumeVersionId() != null) {
            ownedVersion(request.getResumeVersionId());
        }
        if (request.getTargetJobId() != null) {
            ownedTargetJob(request.getTargetJobId(), userId);
        }
        return request;
    }

    private TargetJob ownedTargetJob(Long targetJobId, Long userId) {
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target job does not exist or is unavailable");
        }
        return targetJob;
    }

    private void syncApplicationStatusFromEvent(JobApplication app, JobApplicationEvent event) {
        String nextStatus = statusFromEventType(event == null ? null : event.getEventType());
        if (!shouldTransitionApplicationStatus(app == null ? null : app.getStatus(), nextStatus)) {
            return;
        }
        app.setStatus(nextStatus);
        jobApplicationMapper.updateById(app);
    }

    private JobApplicationEvent findExistingInterviewCompletedEvent(JobApplication app, JobApplicationEventSaveDTO dto) {
        if (app == null || app.getId() == null || !"INTERVIEW_COMPLETED".equals(normalizeApplicationStatus(
                dto == null ? null : dto.getEventType()))) {
            return null;
        }
        Map<String, Object> evidence = interviewCompletedEvidence(dto);
        String reportId = text(evidence.get("reportId"));
        String interviewId = text(evidence.get("interviewId"));
        if (!StringUtils.hasText(reportId) && !StringUtils.hasText(interviewId)) {
            return null;
        }
        List<JobApplicationEvent> events = jobApplicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                .eq(JobApplicationEvent::getUserId, app.getUserId())
                .eq(JobApplicationEvent::getApplicationId, app.getId())
                .eq(JobApplicationEvent::getEventType, "INTERVIEW_COMPLETED")
                .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                .orderByDesc(JobApplicationEvent::getEventTime)
                .orderByDesc(JobApplicationEvent::getCreatedAt));
        if (events == null || events.isEmpty()) {
            return null;
        }
        for (JobApplicationEvent event : events) {
            Map<String, Object> existing = readMap(event.getReviewJson());
            String existingReportId = text(existing.get("reportId"));
            String existingInterviewId = text(existing.get("interviewId"));
            if ((StringUtils.hasText(reportId) && Objects.equals(reportId, existingReportId))
                    || (StringUtils.hasText(interviewId) && Objects.equals(interviewId, existingInterviewId))) {
                return event;
            }
        }
        return null;
    }

    private Map<String, Object> interviewCompletedEvidence(JobApplicationEventSaveDTO dto) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (dto == null) {
            return evidence;
        }
        if (dto.getReview() != null) {
            copyEvidenceValue(evidence, dto.getReview(), "reportId");
            copyEvidenceValue(evidence, dto.getReview(), "interviewId");
        }
        if (StringUtils.hasText(dto.getReviewJson())) {
            Map<String, Object> json = readMap(dto.getReviewJson());
            copyEvidenceValue(evidence, json, "reportId");
            copyEvidenceValue(evidence, json, "interviewId");
        }
        return evidence;
    }

    private void copyEvidenceValue(Map<String, Object> target, Map<String, Object> source, String key) {
        if (target == null || source == null || target.containsKey(key)) {
            return;
        }
        Object value = source.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private String statusFromEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return null;
        }
        String normalized = eventType.trim().toUpperCase();
        if ("APPLIED".equals(normalized)
                || "SUBMITTED".equals(normalized)
                || "APPLICATION_SUBMITTED".equals(normalized)) {
            return "APPLIED";
        }
        if ("INTERVIEW".equals(normalized) || normalized.startsWith("INTERVIEW_")) {
            return "INTERVIEWING";
        }
        if ("OFFER".equals(normalized) || "OFFER_RECEIVED".equals(normalized)) {
            return "OFFER";
        }
        if ("REJECTION".equals(normalized) || "REJECTED".equals(normalized)) {
            return "REJECTED";
        }
        if ("CLOSED".equals(normalized)) {
            return "CLOSED";
        }
        return null;
    }

    private boolean isAgentFollowUpCompletionEvent(String eventType) {
        String normalized = normalizeApplicationStatus(eventType);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return statusFromEventType(normalized) != null
                || "FOLLOW_UP".equals(normalized)
                || normalized.startsWith("FOLLOW_UP_");
    }

    private boolean shouldTransitionApplicationStatus(String currentStatus, String nextStatus) {
        String normalizedNext = normalizeApplicationStatus(nextStatus);
        if (!StringUtils.hasText(normalizedNext)) {
            return false;
        }
        String normalizedCurrent = normalizeApplicationStatus(currentStatus);
        if (!StringUtils.hasText(normalizedCurrent)) {
            return true;
        }
        if (Objects.equals(normalizedCurrent, normalizedNext)) {
            return false;
        }
        Integer currentRank = applicationStatusRank(normalizedCurrent);
        Integer nextRank = applicationStatusRank(normalizedNext);
        if (nextRank == null) {
            return false;
        }
        return currentRank == null || nextRank > currentRank;
    }

    private String normalizeApplicationStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
    }

    private Integer applicationStatusRank(String status) {
        return switch (status) {
            case "SAVED" -> 0;
            case "PREPARING" -> 1;
            case "APPLIED" -> 2;
            case "INTERVIEWING" -> 3;
            case "OFFER" -> 4;
            case "REJECTED" -> 5;
            case "CLOSED" -> 6;
            default -> null;
        };
    }

    private void ensureVersionBelongsToResume(Long resumeId, ResumeVersion version) {
        if (!Objects.equals(resumeId, version.getResumeId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历版本不属于当前简历");
        }
    }

    private void clearCurrentVersions(Long resumeId, Long userId) {
        resumeVersionMapper.update(null, new LambdaUpdateWrapper<ResumeVersion>()
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getResumeId, resumeId)
                .set(ResumeVersion::getCurrentFlag, 0));
    }

    private ResumeVersion insertVersionWithRetry(Resume resume, String requestedVersionName, String sourceType,
                                                 Long sourceId, String snapshotJson, boolean current) {
        DuplicateKeyException lastConflict = null;
        for (int attempt = 1; attempt <= VERSION_INSERT_MAX_ATTEMPTS; attempt++) {
            Integer nextNo = nextVersionNo(resume.getId(), resume.getUserId());
            ResumeVersion version = new ResumeVersion();
            version.setUserId(resume.getUserId());
            version.setResumeId(resume.getId());
            version.setVersionNo(nextNo);
            version.setVersionName(StringUtils.hasText(requestedVersionName) ? requestedVersionName : "V" + nextNo);
            version.setSourceType(sourceType);
            version.setSourceId(sourceId);
            version.setSnapshotJson(snapshotJson);
            version.setCurrentFlag(current ? 1 : 0);
            try {
                if (current) {
                    clearCurrentVersions(resume.getId(), resume.getUserId());
                }
                resumeVersionMapper.insert(version);
                return version;
            } catch (DuplicateKeyException conflict) {
                lastConflict = conflict;
            }
        }
        if (lastConflict != null) {
            throw lastConflict;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to allocate a unique resume version number");
    }

    private void lockResume(Resume resume) {
        if (resume != null && resume.getId() != null && resume.getUserId() != null) {
            resumeMapper.lockOwnedResume(resume.getId(), resume.getUserId());
        }
    }

    private Integer nextVersionNo(Long resumeId, Long userId) {
        ResumeVersion latest = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getResumeId, resumeId)
                .orderByDesc(ResumeVersion::getVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private Long currentUserId() {
        return SecurityAssert.requireLoginUserId();
    }

    private Map<String, Object> snapshot(Resume resume) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", resume.getTitle());
        map.put("realName", resume.getRealName());
        map.put("email", resume.getEmail());
        map.put("phone", resume.getPhone());
        map.put("targetPosition", resume.getTargetPosition());
        map.put("skillStack", resume.getSkillStack());
        map.put("workExperience", resume.getWorkExperience());
        map.put("educationExperience", resume.getEducationExperience());
        map.put("summary", resume.getSummary());
        map.put("projects", projectsForSnapshot(resume.getId()).stream()
                .map(this::projectSnapshot)
                .sorted(Comparator
                        .comparingInt((Map<String, Object> project) -> integer(project.get("sortOrder"), 0))
                        .thenComparingInt(project -> integer(project.get("sort"), 0))
                        .thenComparing(this::writeJson))
                .toList());
        map.put("projectSnapshotSource", "RESUME_VERSION");
        return map;
    }

    private List<ResumeProject> projectsForSnapshot(Long resumeId) {
        if (resumeId == null) {
            return List.of();
        }
        List<ResumeProject> projects = resumeProjectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, resumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeProject::getSortOrder)
                .orderByAsc(ResumeProject::getSort));
        return projects == null ? List.of() : projects;
    }

    private Map<String, Object> projectSnapshot(ResumeProject project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectName", project.getProjectName());
        map.put("projectPeriod", project.getProjectPeriod());
        map.put("projectBackground", project.getProjectBackground());
        map.put("role", project.getRole());
        map.put("techStack", project.getTechStack());
        map.put("responsibility", project.getResponsibility());
        map.put("coreFeatures", project.getCoreFeatures());
        map.put("technicalDifficulties", project.getTechnicalDifficulties());
        map.put("optimizationResults", project.getOptimizationResults());
        map.put("description", project.getDescription());
        map.put("highlights", project.getHighlights());
        map.put("sort", project.getSort() == null ? 0 : project.getSort());
        map.put("sortOrder", project.getSortOrder() == null ? 0 : project.getSortOrder());
        return map;
    }

    private void applySnapshot(Resume resume, Map<String, Object> map) {
        resume.setTitle(text(map.get("title")));
        resume.setRealName(text(map.get("realName")));
        resume.setEmail(text(map.get("email")));
        resume.setPhone(text(map.get("phone")));
        resume.setTargetPosition(text(map.get("targetPosition")));
        resume.setSkillStack(text(map.get("skillStack")));
        resume.setWorkExperience(text(map.get("workExperience")));
        resume.setEducationExperience(text(map.get("educationExperience")));
        resume.setSummary(text(map.get("summary")));
    }

    private void restoreProjects(Long resumeId, Map<String, Object> snapshot) {
        if (resumeId == null || snapshot == null || !snapshot.containsKey("projects")) {
            return;
        }
        resumeProjectMapper.delete(new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, resumeId));
        Object rawProjects = snapshot.get("projects");
        if (!(rawProjects instanceof List<?> projects)) {
            return;
        }
        for (Object rawProject : projects) {
            if (!(rawProject instanceof Map<?, ?> projectSnapshot)) {
                continue;
            }
            ResumeProject project = new ResumeProject();
            project.setResumeId(resumeId);
            project.setProjectName(text(projectSnapshot.get("projectName")));
            project.setProjectPeriod(text(projectSnapshot.get("projectPeriod")));
            project.setProjectBackground(text(projectSnapshot.get("projectBackground")));
            project.setRole(text(projectSnapshot.get("role")));
            project.setTechStack(text(projectSnapshot.get("techStack")));
            project.setResponsibility(text(projectSnapshot.get("responsibility")));
            project.setCoreFeatures(text(projectSnapshot.get("coreFeatures")));
            project.setTechnicalDifficulties(text(projectSnapshot.get("technicalDifficulties")));
            project.setOptimizationResults(text(projectSnapshot.get("optimizationResults")));
            project.setDescription(text(projectSnapshot.get("description")));
            project.setHighlights(text(projectSnapshot.get("highlights")));
            project.setSort(integer(projectSnapshot.get("sort"), 0));
            project.setSortOrder(integer(projectSnapshot.get("sortOrder"), project.getSort()));
            resumeProjectMapper.insert(project);
        }
    }

    private void fillApplication(JobApplication app, JobApplicationSaveDTO dto) {
        app.setTargetJobId(dto == null ? null : dto.getTargetJobId());
        app.setResumeVersionId(dto == null ? null : dto.getResumeVersionId());
        app.setMatchReportId(dto == null ? null : dto.getMatchReportId());
        app.setCompanyName(dto == null ? null : dto.getCompanyName());
        app.setJobTitle(StringUtils.hasText(dto == null ? null : dto.getJobTitle()) ? dto.getJobTitle() : "Untitled Job");
        app.setSource(dto == null ? null : dto.getSource());
        String status = normalizeApplicationStatus(dto == null ? null : dto.getStatus());
        app.setStatus(StringUtils.hasText(status) ? status : "SAVED");
        app.setAppliedAt(dto == null ? null : dto.getAppliedAt());
        app.setNextFollowUpAt(dto == null ? null : dto.getNextFollowUpAt());
        app.setNote(dto == null ? null : dto.getNote());
    }

    private ResumeVersionDiffVO buildDiff(Long resumeId, Long sourceVersionId, Long targetVersionId, String sourceLabel,
                                          String targetLabel, Map<String, Object> source, Map<String, Object> target) {
        ResumeVersionDiffVO vo = new ResumeVersionDiffVO();
        vo.setResumeId(resumeId);
        vo.setVersionId(targetVersionId);
        vo.setSourceVersionId(sourceVersionId);
        vo.setTargetVersionId(targetVersionId);
        vo.setSourceLabel(sourceLabel);
        vo.setTargetLabel(targetLabel);
        for (String field : SNAPSHOT_FIELDS) {
            Object sourceValue = source.get(field);
            Object targetValue = target.get(field);
            ResumeVersionDiffVO.FieldDiff diff = new ResumeVersionDiffVO.FieldDiff();
            diff.setField(field);
            diff.setCurrentValue(sourceValue);
            diff.setVersionValue(targetValue);
            diff.setSourceValue(sourceValue);
            diff.setTargetValue(targetValue);
            diff.setChanged(!Objects.equals(sourceValue, targetValue));
            vo.getFields().add(diff);
        }
        return vo;
    }

    private ResumeVersionVO toVersionVO(ResumeVersion version) {
        ResumeVersionVO vo = new ResumeVersionVO();
        vo.setId(version.getId());
        vo.setResumeId(version.getResumeId());
        vo.setVersionNo(version.getVersionNo());
        vo.setVersionName(version.getVersionName());
        vo.setSourceType(version.getSourceType());
        vo.setSourceId(version.getSourceId());
        vo.setCurrentFlag(version.getCurrentFlag());
        vo.setSnapshot(readMap(version.getSnapshotJson()));
        vo.setCreatedAt(version.getCreatedAt());
        return vo;
    }

    private JobApplicationVO toApplicationVO(JobApplication app) {
        JobApplicationVO vo = new JobApplicationVO();
        if (app == null) {
            return vo;
        }
        vo.setId(app.getId());
        vo.setTargetJobId(app.getTargetJobId());
        vo.setResumeVersionId(app.getResumeVersionId());
        vo.setMatchReportId(app.getMatchReportId());
        vo.setCompanyName(app.getCompanyName());
        vo.setJobTitle(app.getJobTitle());
        vo.setSource(app.getSource());
        vo.setStatus(app.getStatus());
        vo.setAppliedAt(app.getAppliedAt());
        vo.setNextFollowUpAt(app.getNextFollowUpAt());
        vo.setNote(app.getNote());
        vo.setCreatedAt(app.getCreatedAt());
        vo.setUpdatedAt(app.getUpdatedAt());
        return vo;
    }

    private JobApplicationVO toApplicationVOWithDetails(JobApplication app) {
        List<JobApplicationVO> list = toApplicationVOList(app == null ? List.of() : List.of(app));
        return list.isEmpty() ? toApplicationVO(app) : list.get(0);
    }

    private List<JobApplicationVO> toApplicationVOList(List<JobApplication> applications) {
        if (applications == null || applications.isEmpty()) {
            return List.of();
        }
        Map<Long, ResumeVersion> versions = resumeVersionMap(applications);
        Map<Long, JobApplicationEvent> latestEvents = latestApplicationEventMap(applications);
        return applications.stream()
                .map(app -> enrichApplicationVO(toApplicationVO(app),
                        app.getResumeVersionId() == null ? null : versions.get(app.getResumeVersionId()),
                        app.getId() == null ? null : latestEvents.get(app.getId())))
                .toList();
    }

    private JobApplicationVO enrichApplicationVO(JobApplicationVO vo, ResumeVersion version, JobApplicationEvent event) {
        if (vo == null) {
            return null;
        }
        if (version != null) {
            vo.setResumeId(version.getResumeId());
            vo.setResumeVersionNo(version.getVersionNo());
            vo.setResumeVersionName(version.getVersionName());
            vo.setResumeVersionCurrentFlag(version.getCurrentFlag());
        }
        if (event != null) {
            vo.setLatestEventId(event.getId());
            vo.setLatestEventType(event.getEventType());
            vo.setLatestEventTime(event.getEventTime());
            vo.setLatestEventSummary(event.getSummary());
        }
        return vo;
    }

    private JobApplicationAgentContextVO toAgentApplicationContextVO(JobApplication app, LocalDateTime now) {
        JobApplicationAgentContextVO vo = new JobApplicationAgentContextVO();
        vo.setId(app.getId());
        vo.setTargetJobId(app.getTargetJobId());
        vo.setResumeVersionId(app.getResumeVersionId());
        vo.setMatchReportId(app.getMatchReportId());
        vo.setCompanyName(app.getCompanyName());
        vo.setJobTitle(app.getJobTitle());
        vo.setSource(app.getSource());
        vo.setStatus(app.getStatus());
        vo.setAppliedAt(app.getAppliedAt());
        vo.setNextFollowUpAt(app.getNextFollowUpAt());
        fillFollowUpState(vo, app.getNextFollowUpAt(), now);
        vo.setNote(app.getNote());
        vo.setCreatedAt(app.getCreatedAt());
        vo.setUpdatedAt(app.getUpdatedAt());
        return vo;
    }

    private List<JobApplicationAgentContextVO> toAgentApplicationContextVOList(List<JobApplication> applications,
                                                                              LocalDateTime now) {
        if (applications == null || applications.isEmpty()) {
            return List.of();
        }
        Map<Long, ResumeVersion> versions = resumeVersionMap(applications);
        Map<Long, JobApplicationEvent> latestEvents = latestApplicationEventMap(applications);
        return applications.stream()
                .map(app -> enrichAgentContextVO(toAgentApplicationContextVO(app, now),
                        app.getResumeVersionId() == null ? null : versions.get(app.getResumeVersionId()),
                        app.getId() == null ? null : latestEvents.get(app.getId())))
                .toList();
    }

    private JobApplicationAgentContextVO enrichAgentContextVO(JobApplicationAgentContextVO vo, ResumeVersion version,
                                                              JobApplicationEvent event) {
        if (vo == null) {
            return null;
        }
        if (version != null) {
            vo.setResumeId(version.getResumeId());
            vo.setResumeVersionNo(version.getVersionNo());
            vo.setResumeVersionName(version.getVersionName());
            vo.setResumeVersionCurrentFlag(version.getCurrentFlag());
        }
        if (event != null) {
            vo.setLatestEventId(event.getId());
            vo.setLatestEventType(event.getEventType());
            vo.setLatestEventTime(event.getEventTime());
            vo.setLatestEventSummary(event.getSummary());
        }
        return vo;
    }

    private Map<Long, ResumeVersion> resumeVersionMap(List<JobApplication> applications) {
        Set<Long> versionIds = applications.stream()
                .map(JobApplication::getResumeVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        List<ResumeVersion> versions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .in(ResumeVersion::getId, versionIds)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO));
        if (versions == null || versions.isEmpty()) {
            return Map.of();
        }
        return versions.stream()
                .filter(version -> version != null && version.getId() != null)
                .collect(Collectors.toMap(ResumeVersion::getId, version -> version, (left, right) -> left));
    }

    private Map<Long, ResumeVersion> includeCurrentResumeVersions(Map<Long, ResumeVersion> versionMap) {
        if (versionMap == null || versionMap.isEmpty()) {
            return Map.of();
        }
        Set<Long> resumeIds = versionMap.values().stream()
                .filter(Objects::nonNull)
                .map(ResumeVersion::getResumeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (resumeIds.isEmpty()) {
            return versionMap;
        }
        List<ResumeVersion> currentVersions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .in(ResumeVersion::getResumeId, resumeIds)
                .eq(ResumeVersion::getCurrentFlag, 1)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO));
        if (currentVersions == null || currentVersions.isEmpty()) {
            return versionMap;
        }
        Map<Long, ResumeVersion> result = new HashMap<>(versionMap);
        currentVersions.stream()
                .filter(version -> version != null && version.getId() != null)
                .forEach(version -> result.putIfAbsent(version.getId(), version));
        return result;
    }

    private Map<Long, JobApplicationEvent> latestApplicationEventMap(List<JobApplication> applications) {
        Set<Long> applicationIds = applications.stream()
                .map(JobApplication::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        List<JobApplicationEvent> events = jobApplicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                .in(JobApplicationEvent::getApplicationId, applicationIds)
                .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                .orderByDesc(JobApplicationEvent::getEventTime)
                .orderByDesc(JobApplicationEvent::getCreatedAt)
                .orderByDesc(JobApplicationEvent::getId));
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        Map<Long, JobApplicationEvent> latest = new HashMap<>();
        for (JobApplicationEvent event : events) {
            if (event != null && event.getApplicationId() != null) {
                latest.putIfAbsent(event.getApplicationId(), event);
            }
        }
        return latest;
    }

    private JobApplicationSummaryVO toApplicationSummaryVO(JobApplication app) {
        if (app == null) {
            return null;
        }
        JobApplicationSummaryVO vo = new JobApplicationSummaryVO();
        vo.setId(app.getId());
        vo.setUserId(app.getUserId());
        vo.setTargetJobId(app.getTargetJobId());
        vo.setResumeVersionId(app.getResumeVersionId());
        vo.setMatchReportId(app.getMatchReportId());
        vo.setStatus(app.getStatus());
        return vo;
    }

    private void fillFollowUpState(JobApplicationAgentContextVO vo, LocalDateTime nextFollowUpAt, LocalDateTime now) {
        if (nextFollowUpAt == null || now == null) {
            vo.setFollowUpOverdue(false);
            vo.setFollowUpDueToday(false);
            vo.setDaysUntilFollowUp(null);
            return;
        }
        vo.setFollowUpOverdue(nextFollowUpAt.isBefore(now));
        vo.setFollowUpDueToday(nextFollowUpAt.toLocalDate().equals(now.toLocalDate()));
        vo.setDaysUntilFollowUp(ChronoUnit.DAYS.between(now.toLocalDate(), nextFollowUpAt.toLocalDate()));
    }

    private boolean isReminderCandidate(JobApplication app, LocalDate reminderDate, LocalDateTime now) {
        if (app == null || app.getNextFollowUpAt() == null) {
            return false;
        }
        String status = normalizeApplicationStatus(app.getStatus());
        if (!AGENT_APPLICATION_ACTIVE_STATUSES.contains(status)) {
            return false;
        }
        LocalDateTime nextFollowUpAt = app.getNextFollowUpAt();
        return nextFollowUpAt.isBefore(now) || nextFollowUpAt.toLocalDate().equals(reminderDate);
    }

    private boolean isApplicationFollowUpOverdue(JobApplication app, LocalDateTime now) {
        return app != null && app.getNextFollowUpAt() != null && app.getNextFollowUpAt().isBefore(now);
    }

    private ApplicationReminderCandidateVO toApplicationReminderCandidateVO(JobApplication app, LocalDate reminderDate,
                                                                           LocalDateTime now) {
        boolean overdue = isApplicationFollowUpOverdue(app, now);
        ApplicationReminderCandidateVO vo = new ApplicationReminderCandidateVO();
        vo.setType("APPLICATION_FOLLOW_UP_REMINDER");
        vo.setBizType("JOB_APPLICATION");
        vo.setBizId(String.valueOf(app.getId()));
        vo.setTitle(overdue ? "投递跟进已逾期" : "今日待跟进投递");
        vo.setContent(applicationReminderContent(app, overdue));
        vo.setActionUrl(applicationEventActionUrl(app));
        vo.setFallbackPath("/applications");
        vo.setFallbackLabel("查看投递工作台");
        vo.setPlanDate(reminderDate);
        return vo;
    }

    private String applicationEventActionUrl(JobApplication app) {
        if (app == null || app.getId() == null) {
            return "/applications";
        }
        return "/applications?applicationId=" + app.getId() + "&openEvents=1";
    }

    private String applicationReminderContent(JobApplication app, boolean overdue) {
        String company = StringUtils.hasText(app.getCompanyName()) ? app.getCompanyName() : "未填写公司";
        String job = StringUtils.hasText(app.getJobTitle()) ? app.getJobTitle() : "未填写岗位";
        String status = overdue ? "已超过计划跟进时间" : "今天需要跟进";
        return company + " · " + job + " " + status;
    }

    private ResumeSuggestionAdoptionVO toSuggestionAdoptionVO(ResumeSuggestionAdoption adoption) {
        ResumeSuggestionAdoptionVO vo = new ResumeSuggestionAdoptionVO();
        vo.setId(adoption.getId());
        vo.setResumeId(adoption.getResumeId());
        vo.setOptimizeRecordId(adoption.getOptimizeRecordId());
        vo.setResumeVersionId(adoption.getResumeVersionId());
        vo.setSuggestionType(adoption.getSuggestionType());
        vo.setStatus(adoption.getStatus());
        vo.setNote(adoption.getNote());
        vo.setCreatedAt(adoption.getCreatedAt());
        vo.setUpdatedAt(adoption.getUpdatedAt());
        return vo;
    }

    private JobApplicationEventVO toApplicationEventVO(JobApplicationEvent event) {
        JobApplicationEventVO vo = new JobApplicationEventVO();
        vo.setId(event.getId());
        vo.setApplicationId(event.getApplicationId());
        vo.setEventType(event.getEventType());
        vo.setEventTime(event.getEventTime());
        vo.setSummary(event.getSummary());
        vo.setReviewJson(event.getReviewJson());
        vo.setReview(readMap(event.getReviewJson()));
        vo.setCreatedAt(event.getCreatedAt());
        vo.setUpdatedAt(event.getUpdatedAt());
        return vo;
    }

    private Long resolveOptimizeRecordId(ResumeVersion version, ResumeApplyAiSuggestionDTO dto) {
        if (dto != null && dto.getOptimizeRecordId() != null) {
            return dto.getOptimizeRecordId();
        }
        String sourceType = version.getSourceType();
        if (StringUtils.hasText(sourceType)
                && ("AI".equalsIgnoreCase(sourceType)
                || "OPTIMIZE".equalsIgnoreCase(sourceType)
                || "RESUME_OPTIMIZE".equalsIgnoreCase(sourceType))) {
            return version.getSourceId();
        }
        return null;
    }

    private String writeReviewJson(JobApplicationEventSaveDTO dto) {
        if (dto == null) {
            return null;
        }
        Map<String, Object> review = new LinkedHashMap<>();
        if (StringUtils.hasText(dto.getReviewJson())) {
            review.putAll(readMap(dto.getReviewJson()));
        }
        if (dto.getReview() != null) {
            review.putAll(dto.getReview());
        }
        if (!review.isEmpty()) {
            return writeJson(review);
        }
        return StringUtils.hasText(dto.getReviewJson()) ? dto.getReviewJson() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.valueOf(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
