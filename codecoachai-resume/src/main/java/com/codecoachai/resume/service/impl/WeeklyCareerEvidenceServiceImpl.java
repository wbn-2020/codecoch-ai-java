package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.domain.entity.JobSearchExperimentRelation;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO.ApplicationEventItem;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO.ApplicationItem;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO.CalendarEventItem;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO.ExperimentItem;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO.ExperimentRelationItem;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentRelationMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.service.WeeklyCareerEvidenceService;
import com.codecoachai.resume.service.support.ResumeGenerationHashUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WeeklyCareerEvidenceServiceImpl implements WeeklyCareerEvidenceService {

    static final int MAX_APPLICATIONS = 500;
    static final int MAX_APPLICATION_EVENTS = 2000;
    static final int MAX_CALENDAR_EVENTS = 500;
    static final int MAX_EXPERIMENTS = 20;
    static final int MAX_EXPERIMENT_RELATIONS = 2000;

    private static final Pattern SAFE_CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_.:-]{0,63}");
    private static final Pattern LONG_DIGIT_PATTERN = Pattern.compile(".*\\d{8,}.*");
    private static final Set<String> APPLICATION_STATUSES = Set.of(
            "SAVED", "PREPARING", "APPLIED", "INTERVIEWING", "OFFER",
            "REJECTED", "CLOSED", "WITHDRAWN");
    private static final Set<String> EXPERIMENT_STATUSES = Set.of(
            "DRAFT", "RUNNING", "REVIEWED", "ARCHIVED");
    private static final Set<String> RELATION_TYPES = Set.of(
            "RESUME_VERSION", "TARGET_JOB", "JD_ANALYSIS", "JOB_APPLICATION",
            "INTERVIEW_REPORT", "INTERVIEW_SESSION", "PROJECT_EVIDENCE",
            "ABILITY_PROFILE", "AGENT_TASK", "MATCH_REPORT");

    private final JobApplicationMapper jobApplicationMapper;
    private final JobApplicationEventMapper jobApplicationEventMapper;
    private final CareerCalendarEventMapper careerCalendarEventMapper;
    private final JobSearchExperimentMapper experimentMapper;
    private final JobSearchExperimentRelationMapper relationMapper;
    private final TargetJobMapper targetJobMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final ResumeJobMatchReportMapper matchReportMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final UserAbilityProfileMapper abilityProfileMapper;
    private final ObjectMapper objectMapper;

    @Override
    public WeeklyCareerEvidenceVO getWeeklyEvidence(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId,
            String timezone,
            List<Long> experimentIds) {
        RequestWindow window = validateRequest(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                timezone,
                experimentIds);
        TargetJob scopedTarget = requireOwnedTarget(window.userId(), window.targetJobId());
        CollectionState state = new CollectionState();

        List<JobApplication> applicationCandidates = trim(
                jobApplicationMapper.selectWeeklyEvidenceApplications(
                        window.userId(),
                        window.rangeStartUtc(),
                        window.rangeEndUtc(),
                        window.sourceCutoffAt(),
                        window.targetJobId(),
                        MAX_APPLICATIONS + 1),
                MAX_APPLICATIONS,
                "APPLICATIONS_TRUNCATED",
                state);
        List<JobApplicationEvent> eventCandidates = trim(
                jobApplicationEventMapper.selectWeeklyEvidenceEvents(
                        window.userId(),
                        window.rangeStartUtc(),
                        window.rangeEndUtc(),
                        window.sourceCutoffAt(),
                        window.targetJobId(),
                        MAX_APPLICATION_EVENTS + 1),
                MAX_APPLICATION_EVENTS,
                "APPLICATION_EVENTS_TRUNCATED",
                state);
        List<CareerCalendarEvent> calendarCandidates = trim(
                careerCalendarEventMapper.selectWeeklyEvidenceEvents(
                        window.userId(),
                        window.rangeStartUtc(),
                        window.rangeEndUtc(),
                        window.sourceCutoffAt(),
                        window.targetJobId(),
                        MAX_CALENDAR_EVENTS + 1),
                MAX_CALENDAR_EVENTS,
                "CALENDAR_EVENTS_TRUNCATED",
                state);
        List<JobSearchExperiment> experimentCandidates = trim(
                experimentMapper.selectWeeklyEvidenceExperiments(
                        window.userId(),
                        window.rangeStartDate(),
                        window.rangeEndDateExclusive(),
                        window.sourceCutoffAt(),
                        window.targetJobId(),
                        window.experimentIds(),
                        MAX_EXPERIMENTS + 1),
                MAX_EXPERIMENTS,
                "EXPERIMENTS_TRUNCATED",
                state);

        List<Long> candidateExperimentIds = experimentCandidates.stream()
                .filter(Objects::nonNull)
                .map(JobSearchExperiment::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<JobSearchExperimentRelation> relationCandidates = candidateExperimentIds.isEmpty()
                ? List.of()
                : trim(
                        relationMapper.selectWeeklyEvidenceRelations(
                                window.userId(),
                                candidateExperimentIds,
                                window.sourceCutoffAt(),
                                MAX_EXPERIMENT_RELATIONS + 1),
                        MAX_EXPERIMENT_RELATIONS,
                        "EXPERIMENT_RELATIONS_TRUNCATED",
                        state);

        Map<Long, JobApplication> ownedApplications = loadOwnedApplications(
                window,
                applicationCandidates,
                eventCandidates,
                calendarCandidates,
                relationCandidates);
        RelationOwnershipIndex relationOwnership = loadRelationOwnership(
                window,
                scopedTarget,
                relationCandidates,
                ownedApplications);

        List<ApplicationItem> applications = buildApplications(
                window, applicationCandidates, relationOwnership, state);
        List<ApplicationEventItem> applicationEvents = buildApplicationEvents(
                window, eventCandidates, ownedApplications, relationOwnership, state);
        List<CalendarEventItem> calendarEvents = buildCalendarEvents(
                window, calendarCandidates, ownedApplications, relationOwnership, state);
        List<ExperimentItem> experiments = buildExperiments(
                window,
                experimentCandidates,
                relationCandidates,
                relationOwnership,
                state);

        WeeklyCareerEvidenceVO result = new WeeklyCareerEvidenceVO();
        result.setUserId(window.userId());
        result.setApplications(applications);
        result.setApplicationEvents(applicationEvents);
        result.setCalendarEvents(calendarEvents);
        result.setExperiments(experiments);
        fillSourceCounts(result, applications, applicationEvents, calendarEvents, experiments, state);
        result.setTruncated(state.truncated);
        result.setWarnings(new ArrayList<>(state.warnings));
        result.setConsistencyLevel(state.truncated
                ? "BEST_EFFORT"
                : state.isPartial() ? "PARTIAL" : "COMPLETE");
        return result;
    }

    private RequestWindow validateRequest(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId,
            String timezone,
            List<Long> experimentIds) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId 必须为正整数");
        }
        if (rangeStartUtc == null || rangeEndUtc == null || sourceCutoffAt == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "rangeStartUtc、rangeEndUtc 和 sourceCutoffAt 不能为空");
        }
        if (!rangeStartUtc.isBefore(rangeEndUtc)) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "rangeStartUtc 必须早于 rangeEndUtc");
        }
        if (targetJobId != null && targetJobId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "targetJobId 必须为正整数");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "timezone 必须是有效的 ZoneId");
        }

        LinkedHashSet<Long> normalizedExperimentIds = new LinkedHashSet<>();
        if (experimentIds != null) {
            for (Long experimentId : experimentIds) {
                if (experimentId == null) {
                    continue;
                }
                if (experimentId <= 0) {
                    throw new BusinessException(
                            ErrorCode.PARAM_ERROR,
                            "experimentIds 只能包含正整数");
                }
                normalizedExperimentIds.add(experimentId);
            }
        }

        ZonedDateTime localStart = rangeStartUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneId);
        ZonedDateTime localEnd = rangeEndUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneId);
        LocalDate rangeEndDateExclusive = localEnd.toLocalDate();
        if (!LocalTime.MIDNIGHT.equals(localEnd.toLocalTime())) {
            rangeEndDateExclusive = rangeEndDateExclusive.plusDays(1);
        }
        return new RequestWindow(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                zoneId,
                localStart.toLocalDate(),
                rangeEndDateExclusive,
                normalizedExperimentIds.isEmpty()
                        ? null
                        : new ArrayList<>(normalizedExperimentIds),
                normalizedExperimentIds);
    }

    private TargetJob requireOwnedTarget(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return null;
        }
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null
                || !Objects.equals(targetJob.getId(), targetJobId)
                || !Objects.equals(targetJob.getUserId(), userId)
                || isDeleted(targetJob.getDeleted())) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "目标岗位不存在或当前用户无权访问");
        }
        return targetJob;
    }

    private Map<Long, JobApplication> loadOwnedApplications(
            RequestWindow window,
            List<JobApplication> applications,
            List<JobApplicationEvent> events,
            List<CareerCalendarEvent> calendarEvents,
            List<JobSearchExperimentRelation> relations) {
        Map<Long, JobApplication> result = new LinkedHashMap<>();
        for (JobApplication application : safeList(applications)) {
            if (isOwnedApplication(application, window.userId(), window.sourceCutoffAt())) {
                result.put(application.getId(), application);
            }
        }

        LinkedHashSet<Long> requiredIds = new LinkedHashSet<>();
        safeList(events).stream()
                .filter(Objects::nonNull)
                .map(JobApplicationEvent::getApplicationId)
                .filter(Objects::nonNull)
                .forEach(requiredIds::add);
        safeList(calendarEvents).stream()
                .filter(Objects::nonNull)
                .map(CareerCalendarEvent::getApplicationId)
                .filter(Objects::nonNull)
                .forEach(requiredIds::add);
        safeList(relations).stream()
                .filter(Objects::nonNull)
                .filter(relation -> "JOB_APPLICATION".equals(normalizeRelationType(relation.getRelationType())))
                .map(JobSearchExperimentRelation::getRelationId)
                .filter(Objects::nonNull)
                .forEach(requiredIds::add);
        requiredIds.removeAll(result.keySet());

        if (!requiredIds.isEmpty()) {
            List<JobApplication> loaded = jobApplicationMapper.selectWeeklyEvidenceOwnedApplications(
                    window.userId(),
                    new ArrayList<>(requiredIds),
                    window.sourceCutoffAt());
            for (JobApplication application : safeList(loaded)) {
                if (isOwnedApplication(application, window.userId(), window.sourceCutoffAt())) {
                    result.put(application.getId(), application);
                }
            }
        }
        return result;
    }

    private RelationOwnershipIndex loadRelationOwnership(
            RequestWindow window,
            TargetJob scopedTarget,
            List<JobSearchExperimentRelation> relations,
            Map<Long, JobApplication> applications) {
        Map<String, Set<Long>> relationIds = safeList(relations).stream()
                .filter(Objects::nonNull)
                .filter(relation -> relation.getRelationId() != null)
                .collect(Collectors.groupingBy(
                        relation -> normalizeRelationType(relation.getRelationType()),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                JobSearchExperimentRelation::getRelationId,
                                Collectors.toCollection(LinkedHashSet::new))));

        RelationOwnershipIndex index = new RelationOwnershipIndex();
        index.applications.putAll(applications);
        index.resumeVersions.putAll(loadOwnedResumeVersions(
                window,
                relationIds.get("RESUME_VERSION")));
        index.jdAnalyses.putAll(loadOwnedJdAnalyses(
                window,
                relationIds.get("JD_ANALYSIS")));
        index.matchReports.putAll(loadOwnedMatchReports(
                window,
                relationIds.get("MATCH_REPORT")));
        index.projectEvidence.putAll(loadOwnedProjectEvidence(
                window,
                relationIds.get("PROJECT_EVIDENCE")));
        index.abilityProfiles.putAll(loadOwnedAbilityProfiles(
                window,
                relationIds.get("ABILITY_PROFILE")));

        LinkedHashSet<Long> targetIds = new LinkedHashSet<>();
        Set<Long> relatedTargetIds = relationIds.get("TARGET_JOB");
        if (relatedTargetIds != null) {
            targetIds.addAll(relatedTargetIds);
        }
        index.applications.values().stream()
                .map(JobApplication::getTargetJobId)
                .filter(Objects::nonNull)
                .forEach(targetIds::add);
        index.jdAnalyses.values().stream()
                .map(JobDescriptionAnalysis::getTargetJobId)
                .filter(Objects::nonNull)
                .forEach(targetIds::add);
        index.matchReports.values().stream()
                .map(ResumeJobMatchReport::getTargetJobId)
                .filter(Objects::nonNull)
                .forEach(targetIds::add);
        index.projectEvidence.values().stream()
                .map(ProjectEvidence::getTargetJobId)
                .filter(Objects::nonNull)
                .forEach(targetIds::add);
        if (scopedTarget != null && scopedTarget.getId() != null) {
            targetIds.add(scopedTarget.getId());
        }
        index.targetJobs.putAll(loadOwnedTargets(window, targetIds));
        if (scopedTarget != null
                && isOwnedTarget(scopedTarget, window.userId(), window.sourceCutoffAt())) {
            index.targetJobs.put(scopedTarget.getId(), scopedTarget);
        }
        return index;
    }

    private Map<Long, TargetJob> loadOwnedTargets(RequestWindow window, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return safeList(targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                        .eq(TargetJob::getUserId, window.userId())
                        .eq(TargetJob::getDeleted, CommonConstants.NO)
                        .in(TargetJob::getId, ids)))
                .stream()
                .filter(target -> isOwnedTarget(target, window.userId(), window.sourceCutoffAt()))
                .collect(toIdMap(TargetJob::getId));
    }

    private Map<Long, ResumeVersion> loadOwnedResumeVersions(RequestWindow window, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return safeList(resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getUserId, window.userId())
                        .eq(ResumeVersion::getDeleted, CommonConstants.NO)
                        .in(ResumeVersion::getId, ids)))
                .stream()
                .filter(version -> isOwned(
                        version == null ? null : version.getUserId(),
                        version == null ? null : version.getDeleted(),
                        version == null ? null : version.getCreatedAt(),
                        version == null ? null : version.getUpdatedAt(),
                        null,
                        window))
                .collect(toIdMap(ResumeVersion::getId));
    }

    private Map<Long, JobDescriptionAnalysis> loadOwnedJdAnalyses(RequestWindow window, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return safeList(jobDescriptionAnalysisMapper.selectList(
                        new LambdaQueryWrapper<JobDescriptionAnalysis>()
                                .eq(JobDescriptionAnalysis::getUserId, window.userId())
                                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                                .in(JobDescriptionAnalysis::getId, ids)))
                .stream()
                .filter(analysis -> isOwned(
                        analysis == null ? null : analysis.getUserId(),
                        analysis == null ? null : analysis.getDeleted(),
                        analysis == null ? null : analysis.getCreatedAt(),
                        analysis == null ? null : analysis.getUpdatedAt(),
                        null,
                        window))
                .collect(toIdMap(JobDescriptionAnalysis::getId));
    }

    private Map<Long, ResumeJobMatchReport> loadOwnedMatchReports(RequestWindow window, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return safeList(matchReportMapper.selectList(new LambdaQueryWrapper<ResumeJobMatchReport>()
                        .eq(ResumeJobMatchReport::getUserId, window.userId())
                        .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                        .in(ResumeJobMatchReport::getId, ids)))
                .stream()
                .filter(report -> isOwned(
                        report == null ? null : report.getUserId(),
                        report == null ? null : report.getDeleted(),
                        report == null ? null : report.getCreatedAt(),
                        report == null ? null : report.getUpdatedAt(),
                        null,
                        window))
                .collect(toIdMap(ResumeJobMatchReport::getId));
    }

    private Map<Long, ProjectEvidence> loadOwnedProjectEvidence(RequestWindow window, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return safeList(projectEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectEvidence>()
                        .eq(ProjectEvidence::getUserId, window.userId())
                        .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                        .in(ProjectEvidence::getId, ids)))
                .stream()
                .filter(evidence -> isOwned(
                        evidence == null ? null : evidence.getUserId(),
                        evidence == null ? null : evidence.getDeleted(),
                        evidence == null ? null : evidence.getCreatedAt(),
                        evidence == null ? null : evidence.getUpdatedAt(),
                        null,
                        window))
                .collect(toIdMap(ProjectEvidence::getId));
    }

    private Map<Long, UserAbilityProfile> loadOwnedAbilityProfiles(RequestWindow window, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return safeList(abilityProfileMapper.selectList(new LambdaQueryWrapper<UserAbilityProfile>()
                        .eq(UserAbilityProfile::getUserId, window.userId())
                        .eq(UserAbilityProfile::getDeleted, CommonConstants.NO)
                        .in(UserAbilityProfile::getId, ids)))
                .stream()
                .filter(profile -> isOwned(
                        profile == null ? null : profile.getUserId(),
                        profile == null ? null : profile.getDeleted(),
                        profile == null ? null : profile.getCreatedAt(),
                        profile == null ? null : profile.getUpdatedAt(),
                        profile == null ? null : profile.getLastEvaluatedAt(),
                        window))
                .collect(toIdMap(UserAbilityProfile::getId));
    }

    private List<ApplicationItem> buildApplications(
            RequestWindow window,
            List<JobApplication> candidates,
            RelationOwnershipIndex ownership,
            CollectionState state) {
        List<JobApplication> sorted = new ArrayList<>(safeList(candidates));
        sorted.sort(Comparator
                .comparing(this::applicationBusinessTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JobApplication::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<ApplicationItem> result = new ArrayList<>();
        for (JobApplication application : sorted) {
            if (!isApplicationActivityInScope(application, window)) {
                state.exclude("applications", "APPLICATION_RECORDS_EXCLUDED");
                continue;
            }
            LocalDateTime businessTime = applicationBusinessTime(application);
            String timeSource = applicationTimeSource(application);
            String channelKey = channelKey(application.getSource());
            String status = normalizeApplicationStatus(application.getStatus());
            Long safeTargetJobId = safeTargetJobId(application.getTargetJobId(), ownership, state);

            ApplicationItem item = new ApplicationItem();
            item.setApplicationId(application.getId());
            item.setTargetJobId(safeTargetJobId);
            item.setResumeVersionId(application.getResumeVersionId());
            item.setChannelKey(channelKey);
            item.setSource(channelKey);
            item.setAppliedAt(application.getAppliedAt());
            item.setCreatedAt(application.getCreatedAt());
            item.setUpdatedAt(application.getUpdatedAt());
            item.setCurrentStatus(status);
            item.setIncluded(true);
            item.setSourceHash(sourceHash(
                    "APPLICATION",
                    application.getId(),
                    safeTargetJobId,
                    application.getResumeVersionId(),
                    channelKey,
                    businessTime,
                    timeSource,
                    status,
                    application.getUpdatedAt()));
            item.setSafeSummary("投递记录：状态=" + status + "，时间来源=" + timeSource);
            item.getMetadata().put("businessTimeSource", timeSource);
            item.getMetadata().put("timeUncertain", "UPDATED_AT".equals(timeSource));
            item.getMetadata().put(
                    "targetAssociationVerified",
                    application.getTargetJobId() == null || safeTargetJobId != null);
            result.add(item);
        }
        return result;
    }

    private List<ApplicationEventItem> buildApplicationEvents(
            RequestWindow window,
            List<JobApplicationEvent> candidates,
            Map<Long, JobApplication> applications,
            RelationOwnershipIndex ownership,
            CollectionState state) {
        List<JobApplicationEvent> sorted = new ArrayList<>(safeList(candidates));
        sorted.sort(Comparator
                .comparing(JobApplicationEvent::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JobApplicationEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<ApplicationEventItem> result = new ArrayList<>();
        for (JobApplicationEvent event : sorted) {
            JobApplication application = event == null ? null : applications.get(event.getApplicationId());
            if (!isApplicationEventInScope(event, application, window)) {
                state.exclude("applicationEvents", "APPLICATION_EVENT_RECORDS_EXCLUDED");
                continue;
            }
            String eventType = normalizeApplicationEventType(event.getEventType());
            boolean structuredReview = hasStructuredReview(event.getReviewJson());
            Long safeTargetJobId = safeTargetJobId(application.getTargetJobId(), ownership, state);

            ApplicationEventItem item = new ApplicationEventItem();
            item.setEventId(event.getId());
            item.setApplicationId(application.getId());
            item.setTargetJobId(safeTargetJobId);
            item.setEventType(eventType);
            item.setEventTime(event.getEventTime());
            item.setUpdatedAt(event.getUpdatedAt());
            item.setStructuredReview(structuredReview);
            item.setIncluded(true);
            item.setSourceHash(sourceHash(
                    "APPLICATION_EVENT",
                    event.getId(),
                    application.getId(),
                    safeTargetJobId,
                    eventType,
                    event.getEventTime(),
                    event.getUpdatedAt(),
                    structuredReview));
            item.setSafeSummary(
                    "投递事件：类型=" + eventType + "，已有结构化复盘=" + structuredReview);
            item.getMetadata().put("eventCategory", eventType);
            item.getMetadata().put("reviewSchemaPresent", structuredReview);
            item.getMetadata().put(
                    "targetAssociationVerified",
                    application.getTargetJobId() == null || safeTargetJobId != null);
            result.add(item);
        }
        return result;
    }

    private List<CalendarEventItem> buildCalendarEvents(
            RequestWindow window,
            List<CareerCalendarEvent> candidates,
            Map<Long, JobApplication> applications,
            RelationOwnershipIndex ownership,
            CollectionState state) {
        List<CareerCalendarEvent> sorted = new ArrayList<>(safeList(candidates));
        sorted.sort(Comparator
                .comparing(CareerCalendarEvent::getStartsAtUtc, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CareerCalendarEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<CalendarEventItem> result = new ArrayList<>();
        for (CareerCalendarEvent event : sorted) {
            JobApplication application = event == null || event.getApplicationId() == null
                    ? null
                    : applications.get(event.getApplicationId());
            if (!isCalendarEventInScope(event, application, window)) {
                state.exclude("calendarEvents", "CALENDAR_EVENT_RECORDS_EXCLUDED");
                continue;
            }
            String status = normalizeCalendarStatus(event.getStatus());
            String eventType = normalizeCalendarEventType(event.getEventType(), status);
            String sourceType = normalizeCalendarSourceType(event.getSourceType());
            Long safeTargetJobId = application == null
                    ? null
                    : safeTargetJobId(application.getTargetJobId(), ownership, state);

            CalendarEventItem item = new CalendarEventItem();
            item.setEventId(event.getId());
            item.setApplicationId(application == null ? null : application.getId());
            item.setTargetJobId(safeTargetJobId);
            item.setEventType(eventType);
            item.setStartsAtUtc(event.getStartsAtUtc());
            item.setEndsAtUtc(event.getEndsAtUtc());
            item.setUpdatedAt(event.getUpdatedAt());
            item.setStatus(status);
            item.setSourceType(sourceType);
            item.setIncluded(true);
            item.setSourceHash(sourceHash(
                    "CALENDAR_EVENT",
                    event.getId(),
                    item.getApplicationId(),
                    item.getTargetJobId(),
                    eventType,
                    event.getStartsAtUtc(),
                    event.getEndsAtUtc(),
                    status,
                    sourceType,
                    event.getUpdatedAt()));
            item.setSafeSummary("日历事件：类型=" + eventType + "，状态=" + status);
            item.getMetadata().put("linkedApplication", application != null);
            item.getMetadata().put("allDay", Objects.equals(event.getAllDayFlag(), CommonConstants.YES));
            item.getMetadata().put(
                    "targetAssociationVerified",
                    application == null
                            || application.getTargetJobId() == null
                            || safeTargetJobId != null);
            result.add(item);
        }
        return result;
    }

    private List<ExperimentItem> buildExperiments(
            RequestWindow window,
            List<JobSearchExperiment> experimentCandidates,
            List<JobSearchExperimentRelation> relationCandidates,
            RelationOwnershipIndex ownership,
            CollectionState state) {
        Set<Long> candidateIds = safeList(experimentCandidates).stream()
                .filter(Objects::nonNull)
                .map(JobSearchExperiment::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<ExperimentRelationItem>> relationsByExperiment = new LinkedHashMap<>();
        for (JobSearchExperimentRelation relation : safeList(relationCandidates)) {
            if (!isOwnedRelationRow(relation, window, candidateIds)) {
                state.exclude("experimentRelations", "EXPERIMENT_RELATION_RECORDS_EXCLUDED");
                continue;
            }
            ExperimentRelationItem item = toRelationItem(relation, window, ownership, state);
            relationsByExperiment
                    .computeIfAbsent(relation.getExperimentId(), ignored -> new ArrayList<>())
                    .add(item);
        }

        List<JobSearchExperiment> sorted = new ArrayList<>(safeList(experimentCandidates));
        sorted.sort(Comparator
                .comparing(JobSearchExperiment::getStartDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(JobSearchExperiment::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<ExperimentItem> result = new ArrayList<>();
        for (JobSearchExperiment experiment : sorted) {
            if (!isExperimentInScope(experiment, window)) {
                state.exclude("experiments", "EXPERIMENT_RECORDS_EXCLUDED");
                continue;
            }
            List<ExperimentRelationItem> relations = relationsByExperiment.getOrDefault(
                    experiment.getId(), List.of());
            if (window.targetJobId() != null && relations.stream().noneMatch(
                    relation -> Boolean.TRUE.equals(relation.getIncluded())
                            && Objects.equals(window.targetJobId(), relation.getTargetJobId()))) {
                state.exclude("experiments", "EXPERIMENT_TARGET_SCOPE_EXCLUDED");
                continue;
            }

            String status = normalizeExperimentStatus(experiment.getStatus());
            String targetDirection = safeCategorical("DIRECTION", experiment.getTargetDirection(), "UNSPECIFIED");
            long includedRelations = relations.stream()
                    .filter(relation -> Boolean.TRUE.equals(relation.getIncluded()))
                    .count();
            long excludedRelations = relations.size() - includedRelations;

            ExperimentItem item = new ExperimentItem();
            item.setExperimentId(experiment.getId());
            item.setStartDate(experiment.getStartDate());
            item.setEndDate(experiment.getEndDate());
            item.setStatus(status);
            item.setTargetDirection(targetDirection);
            item.setDemo(false);
            item.setIncluded(true);
            item.setRelations(new ArrayList<>(relations));
            item.setSourceHash(sourceHash(
                    "EXPERIMENT",
                    experiment.getId(),
                    experiment.getStartDate(),
                    experiment.getEndDate(),
                    status,
                    targetDirection,
                    experiment.getUpdatedAt(),
                    includedRelations,
                    excludedRelations));
            item.setSafeSummary(
                    "策略实验：状态=" + status + "，关联记录数=" + relations.size());
            item.getMetadata().put("relationCount", relations.size());
            item.getMetadata().put("includedRelationCount", includedRelations);
            item.getMetadata().put("excludedRelationCount", excludedRelations);
            result.add(item);
        }
        return result;
    }

    private ExperimentRelationItem toRelationItem(
            JobSearchExperimentRelation relation,
            RequestWindow window,
            RelationOwnershipIndex ownership,
            CollectionState state) {
        String relationType = normalizeRelationType(relation.getRelationType());
        RelationOwnership resolution = resolveRelationOwnership(
                relationType,
                relation.getRelationId(),
                ownership);
        String excludeReason = resolution.excludeReason();
        if (excludeReason == null
                && window.targetJobId() != null
                && resolution.targetJobId() != null
                && !Objects.equals(window.targetJobId(), resolution.targetJobId())) {
            excludeReason = "TARGET_SCOPE_MISMATCH";
        }
        boolean included = excludeReason == null;
        if (!included) {
            state.exclude("experimentRelations", "EXPERIMENT_RELATION_OBJECTS_EXCLUDED");
        }

        ExperimentRelationItem item = new ExperimentRelationItem();
        item.setRelationId(relation.getId());
        item.setRelationType(relationType);
        item.setRelationObjectId(included ? relation.getRelationId() : null);
        item.setTargetJobId(included ? resolution.targetJobId() : null);
        item.setSourceTime(included ? resolution.sourceTime() : relation.getCreatedAt());
        item.setIncluded(included);
        item.setExcludeReason(excludeReason);
        item.setSourceHash(sourceHash(
                "EXPERIMENT_RELATION",
                relation.getId(),
                relation.getExperimentId(),
                relationType,
                relation.getRelationId(),
                resolution.targetJobId(),
                resolution.sourceTime(),
                relation.getUpdatedAt(),
                included,
                excludeReason));
        return item;
    }

    private RelationOwnership resolveRelationOwnership(
            String relationType,
            Long relationObjectId,
            RelationOwnershipIndex ownership) {
        if (relationObjectId == null) {
            return RelationOwnership.excluded("RELATION_OBJECT_ID_MISSING");
        }
        return switch (relationType) {
            case "TARGET_JOB" -> {
                TargetJob target = ownership.targetJobs.get(relationObjectId);
                yield target == null
                        ? RelationOwnership.excluded("RELATION_OBJECT_NOT_OWNED")
                        : RelationOwnership.included(target.getId(), target.getCreatedAt());
            }
            case "JOB_APPLICATION" -> {
                JobApplication application = ownership.applications.get(relationObjectId);
                yield application == null
                        ? RelationOwnership.excluded("RELATION_OBJECT_NOT_OWNED")
                        : relationOwnershipWithVerifiedTarget(
                                application.getTargetJobId(),
                                applicationBusinessTime(application),
                                ownership);
            }
            case "RESUME_VERSION" -> {
                ResumeVersion version = ownership.resumeVersions.get(relationObjectId);
                yield version == null
                        ? RelationOwnership.excluded("RELATION_OBJECT_NOT_OWNED")
                        : RelationOwnership.included(null, version.getCreatedAt());
            }
            case "JD_ANALYSIS" -> {
                JobDescriptionAnalysis analysis = ownership.jdAnalyses.get(relationObjectId);
                yield analysis == null
                        ? RelationOwnership.excluded("RELATION_OBJECT_NOT_OWNED")
                        : relationOwnershipWithVerifiedTarget(
                                analysis.getTargetJobId(),
                                analysis.getCreatedAt(),
                                ownership);
            }
            case "MATCH_REPORT" -> {
                ResumeJobMatchReport report = ownership.matchReports.get(relationObjectId);
                yield report == null
                        ? RelationOwnership.excluded("RELATION_OBJECT_NOT_OWNED")
                        : relationOwnershipWithVerifiedTarget(
                                report.getTargetJobId(),
                                report.getCreatedAt(),
                                ownership);
            }
            case "PROJECT_EVIDENCE" -> {
                ProjectEvidence evidence = ownership.projectEvidence.get(relationObjectId);
                yield evidence == null
                        ? RelationOwnership.excluded("RELATION_OBJECT_NOT_OWNED")
                        : relationOwnershipWithVerifiedTarget(
                                evidence.getTargetJobId(),
                                evidence.getCreatedAt(),
                                ownership);
            }
            case "ABILITY_PROFILE" -> {
                UserAbilityProfile profile = ownership.abilityProfiles.get(relationObjectId);
                yield profile == null
                        ? RelationOwnership.excluded("RELATION_OBJECT_NOT_OWNED")
                        : RelationOwnership.included(
                                null,
                                firstNonNull(profile.getLastEvaluatedAt(), profile.getCreatedAt()));
            }
            case "INTERVIEW_REPORT", "INTERVIEW_SESSION", "AGENT_TASK" ->
                    RelationOwnership.excluded("EXTERNAL_RELATION_NOT_EXPOSED");
            default -> RelationOwnership.excluded("UNSUPPORTED_RELATION_TYPE");
        };
    }

    private RelationOwnership relationOwnershipWithVerifiedTarget(
            Long targetJobId,
            LocalDateTime sourceTime,
            RelationOwnershipIndex ownership) {
        if (targetJobId != null && !ownership.targetJobs.containsKey(targetJobId)) {
            return RelationOwnership.excluded("RELATION_TARGET_NOT_OWNED");
        }
        return RelationOwnership.included(targetJobId, sourceTime);
    }

    private Long safeTargetJobId(
            Long targetJobId,
            RelationOwnershipIndex ownership,
            CollectionState state) {
        if (targetJobId == null || ownership.targetJobs.containsKey(targetJobId)) {
            return targetJobId;
        }
        state.redact("TARGET_ASSOCIATION_REDACTED");
        return null;
    }

    private boolean isApplicationActivityInScope(JobApplication application, RequestWindow window) {
        LocalDateTime businessTime = applicationBusinessTime(application);
        return isOwnedApplication(application, window.userId(), window.sourceCutoffAt())
                && inHalfOpenWindow(businessTime, window.rangeStartUtc(), window.rangeEndUtc())
                && matchesTarget(application.getTargetJobId(), window.targetJobId());
    }

    private boolean isApplicationEventInScope(
            JobApplicationEvent event,
            JobApplication application,
            RequestWindow window) {
        return event != null
                && event.getId() != null
                && Objects.equals(event.getUserId(), window.userId())
                && !isDeleted(event.getDeleted())
                && inHalfOpenWindow(event.getEventTime(), window.rangeStartUtc(), window.rangeEndUtc())
                && visibleAt(
                        event.getCreatedAt(),
                        event.getUpdatedAt(),
                        event.getEventTime(),
                        window.sourceCutoffAt())
                && application != null
                && matchesTarget(application.getTargetJobId(), window.targetJobId());
    }

    private boolean isCalendarEventInScope(
            CareerCalendarEvent event,
            JobApplication application,
            RequestWindow window) {
        if (event == null
                || event.getId() == null
                || !Objects.equals(event.getUserId(), window.userId())
                || isDeleted(event.getDeleted())
                || !inHalfOpenWindow(
                        event.getStartsAtUtc(),
                        window.rangeStartUtc(),
                        window.rangeEndUtc())
                || !visibleAt(
                        event.getCreatedAt(),
                        event.getUpdatedAt(),
                        event.getStartsAtUtc(),
                        window.sourceCutoffAt())) {
            return false;
        }
        if (event.getApplicationId() != null && application == null) {
            return false;
        }
        return window.targetJobId() == null
                || application != null
                && Objects.equals(application.getTargetJobId(), window.targetJobId());
    }

    private boolean isExperimentInScope(JobSearchExperiment experiment, RequestWindow window) {
        if (experiment == null
                || experiment.getId() == null
                || !Objects.equals(experiment.getUserId(), window.userId())
                || isDeleted(experiment.getDeleted())
                || Objects.equals(experiment.getDemoFlag(), CommonConstants.YES)
                || !visibleAt(
                        experiment.getCreatedAt(),
                        experiment.getUpdatedAt(),
                        null,
                        window.sourceCutoffAt())) {
            return false;
        }
        if (!window.requestedExperimentIds().isEmpty()
                && !window.requestedExperimentIds().contains(experiment.getId())) {
            return false;
        }
        return (experiment.getStartDate() == null
                || experiment.getStartDate().isBefore(window.rangeEndDateExclusive()))
                && (experiment.getEndDate() == null
                || !experiment.getEndDate().isBefore(window.rangeStartDate()));
    }

    private boolean isOwnedRelationRow(
            JobSearchExperimentRelation relation,
            RequestWindow window,
            Set<Long> candidateExperimentIds) {
        return relation != null
                && relation.getId() != null
                && relation.getExperimentId() != null
                && candidateExperimentIds.contains(relation.getExperimentId())
                && Objects.equals(relation.getUserId(), window.userId())
                && !isDeleted(relation.getDeleted())
                && !Objects.equals(relation.getDemoFlag(), CommonConstants.YES)
                && visibleAt(
                        relation.getCreatedAt(),
                        relation.getUpdatedAt(),
                        null,
                        window.sourceCutoffAt());
    }

    private boolean isOwnedApplication(
            JobApplication application,
            Long userId,
            LocalDateTime sourceCutoffAt) {
        return application != null
                && application.getId() != null
                && Objects.equals(application.getUserId(), userId)
                && !isDeleted(application.getDeleted())
                && visibleAt(
                        application.getCreatedAt(),
                        application.getUpdatedAt(),
                        application.getAppliedAt(),
                        sourceCutoffAt);
    }

    private boolean isOwnedTarget(TargetJob targetJob, Long userId, LocalDateTime sourceCutoffAt) {
        return targetJob != null
                && targetJob.getId() != null
                && Objects.equals(targetJob.getUserId(), userId)
                && !isDeleted(targetJob.getDeleted())
                && visibleAt(
                        targetJob.getCreatedAt(),
                        targetJob.getUpdatedAt(),
                        null,
                        sourceCutoffAt);
    }

    private boolean isOwned(
            Long ownerUserId,
            Integer deleted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime fallbackTime,
            RequestWindow window) {
        return Objects.equals(ownerUserId, window.userId())
                && !isDeleted(deleted)
                && visibleAt(createdAt, updatedAt, fallbackTime, window.sourceCutoffAt());
    }

    private boolean inHalfOpenWindow(
            LocalDateTime value,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        return value != null && !value.isBefore(rangeStart) && value.isBefore(rangeEnd);
    }

    private boolean visibleAt(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime fallbackTime,
            LocalDateTime sourceCutoffAt) {
        LocalDateTime visibleTime = firstNonNull(createdAt, fallbackTime, updatedAt);
        return visibleTime != null
                && !visibleTime.isAfter(sourceCutoffAt)
                && (updatedAt == null || !updatedAt.isAfter(sourceCutoffAt));
    }

    private boolean matchesTarget(Long recordTargetJobId, Long requestedTargetJobId) {
        return requestedTargetJobId == null
                || Objects.equals(recordTargetJobId, requestedTargetJobId);
    }

    private boolean isDeleted(Integer deleted) {
        return Objects.equals(deleted, CommonConstants.YES);
    }

    private LocalDateTime applicationBusinessTime(JobApplication application) {
        if (application == null) {
            return null;
        }
        return firstNonNull(
                application.getAppliedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private String applicationTimeSource(JobApplication application) {
        if (application != null && application.getAppliedAt() != null) {
            return "APPLIED_AT";
        }
        if (application != null && application.getCreatedAt() != null) {
            return "CREATED_AT";
        }
        return "UPDATED_AT";
    }

    private boolean hasStructuredReview(String reviewJson) {
        if (!StringUtils.hasText(reviewJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(reviewJson);
            JsonNode structuredReview = root == null ? null : root.get("structuredReview");
            return structuredReview != null
                    && structuredReview.isObject()
                    && !structuredReview.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeApplicationStatus(String value) {
        String normalized = simpleCode(value);
        return APPLICATION_STATUSES.contains(normalized) ? normalized : "UNKNOWN";
    }

    private String normalizeApplicationEventType(String value) {
        String normalized = simpleCode(value);
        if (normalized.startsWith("FOLLOW_UP") || "FOLLOWUP".equals(normalized)) {
            return "FOLLOW_UP";
        }
        if (normalized.startsWith("INTERVIEW_INVIT") || "INTERVIEW_SCHEDULED".equals(normalized)) {
            return "INTERVIEW_INVITED";
        }
        if (normalized.startsWith("INTERVIEW_COMPLETED")
                || "INTERVIEW_FEEDBACK_REVIEW".equals(normalized)) {
            return "INTERVIEW_COMPLETED";
        }
        if (normalized.startsWith("OFFER")) {
            return "OFFER";
        }
        if (normalized.startsWith("REJECT") || normalized.startsWith("REJECTION")) {
            return "REJECTED";
        }
        if (normalized.startsWith("CLOSED")
                || normalized.startsWith("WITHDRAW")) {
            return "CLOSED";
        }
        return switch (normalized) {
            case "APPLICATION_CREATED" -> "APPLICATION_CREATED";
            case "NOTE" -> "NOTE";
            default -> "UNKNOWN";
        };
    }

    private String normalizeCalendarEventType(String value, String status) {
        if ("CANCELLED".equals(status)) {
            return "CANCELLED_CALENDAR_EVENT";
        }
        return switch (simpleCode(value)) {
            case "INTERVIEW" -> "SCHEDULED_INTERVIEW";
            case "FOLLOW_UP", "THANK_YOU" -> "SCHEDULED_FOLLOW_UP";
            case "TRAINING", "PRACTICE" -> "SCHEDULED_TRAINING";
            default -> "OTHER_CALENDAR_EVENT";
        };
    }

    private String normalizeCalendarStatus(String value) {
        return switch (simpleCode(value)) {
            case "CONFIRMED" -> "CONFIRMED";
            case "TENTATIVE" -> "TENTATIVE";
            case "CANCELLED", "CANCELED" -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }

    private String normalizeCalendarSourceType(String value) {
        return switch (simpleCode(value)) {
            case "MANUAL" -> "MANUAL";
            case "CSV", "CSV_IMPORT" -> "CSV_IMPORT";
            case "ICS", "ICS_IMPORT" -> "ICS_IMPORT";
            case "SYSTEM" -> "SYSTEM";
            default -> "OTHER";
        };
    }

    private String normalizeExperimentStatus(String value) {
        String normalized = simpleCode(value);
        return EXPERIMENT_STATUSES.contains(normalized) ? normalized : "UNKNOWN";
    }

    private String normalizeRelationType(String value) {
        String normalized = simpleCode(value);
        return RELATION_TYPES.contains(normalized) ? normalized : "UNKNOWN";
    }

    private String simpleCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return SAFE_CODE_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private String channelKey(String source) {
        return safeCategorical("CHANNEL", source, "UNKNOWN");
    }

    private String safeCategorical(String prefix, String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return prefix + ":" + fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(prefix + ":")) {
            normalized = normalized.substring(prefix.length() + 1);
        }
        if (SAFE_CODE_PATTERN.matcher(normalized).matches() && !looksSensitive(normalized)) {
            return prefix + ":" + normalized;
        }
        String digest = ResumeGenerationHashUtils.sha256(objectMapper, List.of(prefix, normalized));
        return prefix + ":HASH_" + digest.substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private boolean looksSensitive(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("@")
                || normalized.contains("TOKEN")
                || normalized.contains("SECRET")
                || normalized.contains("PASSWORD")
                || normalized.contains("API_KEY")
                || normalized.contains("API-KEY")
                || LONG_DIGIT_PATTERN.matcher(normalized).matches();
    }

    private String sourceHash(String sourceType, Object... values) {
        List<String> canonical = new ArrayList<>();
        canonical.add(sourceType);
        for (Object value : values) {
            canonical.add(value == null ? null : String.valueOf(value));
        }
        return "sha256:" + ResumeGenerationHashUtils.sha256(objectMapper, canonical);
    }

    private void fillSourceCounts(
            WeeklyCareerEvidenceVO result,
            List<ApplicationItem> applications,
            List<ApplicationEventItem> applicationEvents,
            List<CalendarEventItem> calendarEvents,
            List<ExperimentItem> experiments,
            CollectionState state) {
        int includedRelations = experiments.stream()
                .flatMap(experiment -> experiment.getRelations().stream())
                .mapToInt(relation -> Boolean.TRUE.equals(relation.getIncluded()) ? 1 : 0)
                .sum();
        putSourceCounts(
                result.getSourceCounts(),
                "applications",
                applications.size(),
                state.excludedCount("applications"));
        putSourceCounts(
                result.getSourceCounts(),
                "applicationEvents",
                applicationEvents.size(),
                state.excludedCount("applicationEvents"));
        putSourceCounts(
                result.getSourceCounts(),
                "calendarEvents",
                calendarEvents.size(),
                state.excludedCount("calendarEvents"));
        putSourceCounts(
                result.getSourceCounts(),
                "experiments",
                experiments.size(),
                state.excludedCount("experiments"));
        putSourceCounts(
                result.getSourceCounts(),
                "experimentRelations",
                includedRelations,
                state.excludedCount("experimentRelations"));
    }

    private void putSourceCounts(
            Map<String, Integer> counts,
            String source,
            int included,
            int excluded) {
        counts.put(source + ".included", included);
        counts.put(source + ".excluded", excluded);
        counts.put(source + ".total", included + excluded);
    }

    private <T> List<T> trim(
            List<T> values,
            int maxSize,
            String warning,
            CollectionState state) {
        List<T> safe = safeList(values);
        if (safe.size() <= maxSize) {
            return safe;
        }
        state.truncated = true;
        state.warnings.add(warning);
        return new ArrayList<>(safe.subList(0, maxSize));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> CollectorFactory<T> toIdMap(Function<T, Long> idExtractor) {
        return new CollectorFactory<>(idExtractor);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record RequestWindow(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId,
            ZoneId zoneId,
            LocalDate rangeStartDate,
            LocalDate rangeEndDateExclusive,
            List<Long> experimentIds,
            Set<Long> requestedExperimentIds) {
    }

    private record RelationOwnership(
            Long targetJobId,
            LocalDateTime sourceTime,
            String excludeReason) {

        private static RelationOwnership included(Long targetJobId, LocalDateTime sourceTime) {
            return new RelationOwnership(targetJobId, sourceTime, null);
        }

        private static RelationOwnership excluded(String excludeReason) {
            return new RelationOwnership(null, null, excludeReason);
        }
    }

    private static final class RelationOwnershipIndex {

        private final Map<Long, JobApplication> applications = new HashMap<>();
        private final Map<Long, TargetJob> targetJobs = new HashMap<>();
        private final Map<Long, ResumeVersion> resumeVersions = new HashMap<>();
        private final Map<Long, JobDescriptionAnalysis> jdAnalyses = new HashMap<>();
        private final Map<Long, ResumeJobMatchReport> matchReports = new HashMap<>();
        private final Map<Long, ProjectEvidence> projectEvidence = new HashMap<>();
        private final Map<Long, UserAbilityProfile> abilityProfiles = new HashMap<>();
    }

    private static final class CollectionState {

        private final Map<String, Integer> excludedCounts = new HashMap<>();
        private final LinkedHashSet<String> warnings = new LinkedHashSet<>();
        private boolean truncated;
        private boolean partial;

        private void exclude(String source, String warning) {
            excludedCounts.merge(source, 1, Integer::sum);
            warnings.add(warning);
            partial = true;
        }

        private void redact(String warning) {
            warnings.add(warning);
            partial = true;
        }

        private int excludedCount(String source) {
            return excludedCounts.getOrDefault(source, 0);
        }

        private boolean isPartial() {
            return partial;
        }
    }

    private static final class CollectorFactory<T>
            implements java.util.stream.Collector<T, Map<Long, T>, Map<Long, T>> {

        private final Function<T, Long> idExtractor;

        private CollectorFactory(Function<T, Long> idExtractor) {
            this.idExtractor = idExtractor;
        }

        @Override
        public java.util.function.Supplier<Map<Long, T>> supplier() {
            return LinkedHashMap::new;
        }

        @Override
        public java.util.function.BiConsumer<Map<Long, T>, T> accumulator() {
            return (map, value) -> {
                if (value != null) {
                    Long id = idExtractor.apply(value);
                    if (id != null) {
                        map.putIfAbsent(id, value);
                    }
                }
            };
        }

        @Override
        public java.util.function.BinaryOperator<Map<Long, T>> combiner() {
            return (left, right) -> {
                right.forEach(left::putIfAbsent);
                return left;
            };
        }

        @Override
        public Function<Map<Long, T>, Map<Long, T>> finisher() {
            return Function.identity();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of(Characteristics.IDENTITY_FINISH);
        }
    }
}
