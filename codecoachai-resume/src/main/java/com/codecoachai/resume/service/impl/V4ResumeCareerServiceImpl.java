package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
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
import com.codecoachai.resume.domain.vo.JobApplicationAgentContextVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventVO;
import com.codecoachai.resume.domain.vo.JobApplicationStatsVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionAdoptionVO;
import com.codecoachai.resume.domain.vo.ResumeVersionDiffVO;
import com.codecoachai.resume.domain.vo.ResumeVersionVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeSuggestionAdoptionMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class V4ResumeCareerServiceImpl implements V4ResumeCareerService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "title", "realName", "email", "phone", "targetPosition", "skillStack",
            "workExperience", "educationExperience", "summary");
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
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeVersionVO createVersion(Long resumeId, ResumeVersionCreateDTO dto) {
        Resume resume = ownedResume(resumeId);
        Integer nextNo = nextVersionNo(resumeId, resume.getUserId());
        ResumeVersion version = new ResumeVersion();
        version.setUserId(resume.getUserId());
        version.setResumeId(resumeId);
        version.setVersionNo(nextNo);
        version.setVersionName(StringUtils.hasText(dto == null ? null : dto.getVersionName()) ? dto.getVersionName() : "V" + nextNo);
        version.setSourceType(StringUtils.hasText(dto == null ? null : dto.getSourceType()) ? dto.getSourceType() : "MANUAL");
        version.setSourceId(dto == null ? null : dto.getSourceId());
        version.setSnapshotJson(writeJson(snapshot(resume)));
        version.setCurrentFlag(1);
        clearCurrentVersions(resumeId, resume.getUserId());
        resumeVersionMapper.insert(version);
        return toVersionVO(version);
    }

    @Override
    public ResumeVersionVO copyVersion(Long resumeId, Long versionId, ResumeVersionCopyDTO dto) {
        Resume resume = ownedResume(resumeId);
        ResumeVersion source = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, source);

        Integer nextNo = nextVersionNo(resumeId, resume.getUserId());
        String sourceName = StringUtils.hasText(source.getVersionName()) ? source.getVersionName() : "V" + source.getVersionNo();
        ResumeVersion copy = new ResumeVersion();
        copy.setUserId(resume.getUserId());
        copy.setResumeId(resumeId);
        copy.setVersionNo(nextNo);
        copy.setVersionName(StringUtils.hasText(dto == null ? null : dto.getVersionName())
                ? dto.getVersionName()
                : "Copy of " + sourceName);
        copy.setSourceType("COPY");
        copy.setSourceId(source.getId());
        copy.setSnapshotJson(StringUtils.hasText(source.getSnapshotJson()) ? source.getSnapshotJson() : writeJson(snapshot(resume)));
        copy.setCurrentFlag(0);
        resumeVersionMapper.insert(copy);
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
        ResumeVersion version = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, version);
        applySnapshot(resume, readMap(version.getSnapshotJson()));
        resumeMapper.updateById(resume);
        clearCurrentVersions(resumeId, resume.getUserId());
        version.setCurrentFlag(1);
        resumeVersionMapper.updateById(version);
        return toVersionVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSuggestionAdoptionVO applyAiSuggestion(Long versionId, ResumeApplyAiSuggestionDTO dto) {
        ResumeVersion version = ownedVersion(versionId);
        Resume resume = ownedResume(version.getResumeId());
        applySnapshot(resume, readMap(version.getSnapshotJson()));
        resumeMapper.updateById(resume);
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
        return toSuggestionAdoptionVO(adoption);
    }

    @Override
    public List<JobApplicationVO> listApplications(String status) {
        Long userId = currentUserId();
        return jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .eq(StringUtils.hasText(status), JobApplication::getStatus, status)
                        .orderByDesc(JobApplication::getUpdatedAt))
                .stream().map(this::toApplicationVO).toList();
    }

    @Override
    public JobApplicationStatsVO getApplicationStats(LocalDateTime now) {
        Long userId = currentUserId();
        LocalDateTime generatedAt = now == null ? LocalDateTime.now() : now;
        List<JobApplication> applications = jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO));
        return buildApplicationStats(applications, generatedAt);
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
        return applications == null
                ? List.of()
                : applications.stream().map(app -> toAgentApplicationContextVO(app, now)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationVO createApplication(JobApplicationSaveDTO dto) {
        Long userId = currentUserId();
        JobApplicationSaveDTO request = prepareApplicationRequest(dto, userId);
        JobApplication existing = findApplicationByMatchReport(request.getMatchReportId(), userId);
        if (existing != null) {
            return toApplicationVO(existing);
        }
        JobApplication app = new JobApplication();
        app.setUserId(userId);
        fillApplication(app, request);
        jobApplicationMapper.insert(app);
        return toApplicationVO(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationVO updateApplication(Long id, JobApplicationSaveDTO dto) {
        JobApplication app = ownedApplication(id);
        JobApplicationSaveDTO request = prepareApplicationRequest(dto, app.getUserId());
        ensureMatchReportNotLinkedToAnotherApplication(request.getMatchReportId(), app.getUserId(), app.getId());
        fillApplication(app, request);
        jobApplicationMapper.updateById(app);
        return toApplicationVO(jobApplicationMapper.selectById(id));
    }

    @Override
    public List<JobApplicationEventVO> listApplicationEvents(Long applicationId) {
        JobApplication app = ownedApplication(applicationId);
        return jobApplicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                        .eq(JobApplicationEvent::getUserId, app.getUserId())
                        .eq(JobApplicationEvent::getApplicationId, applicationId)
                        .orderByDesc(JobApplicationEvent::getEventTime)
                        .orderByDesc(JobApplicationEvent::getCreatedAt))
                .stream().map(this::toApplicationEventVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationEventVO createApplicationEvent(Long applicationId, JobApplicationEventSaveDTO dto) {
        JobApplication app = ownedApplication(applicationId);
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

    private void completeAgentFollowUpAfterCommit(Long userId, Long applicationId, Long eventId) {
        if (userId == null || applicationId == null || eventId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentBusinessActionNotifier.completeApplicationFollowUp(userId, applicationId, eventId);
                }
            });
            return;
        }
        agentBusinessActionNotifier.completeApplicationFollowUp(userId, applicationId, eventId);
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
        JobApplication app = jobApplicationMapper.selectById(applicationId);
        Long userId = currentUserId();
        if (app == null || !Objects.equals(userId, app.getUserId())) {
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
        return request;
    }

    private void syncApplicationStatusFromEvent(JobApplication app, JobApplicationEvent event) {
        String nextStatus = statusFromEventType(event == null ? null : event.getEventType());
        if (!shouldTransitionApplicationStatus(app == null ? null : app.getStatus(), nextStatus)) {
            return;
        }
        app.setStatus(nextStatus);
        jobApplicationMapper.updateById(app);
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
        map.put("projects", projectsForSnapshot(resume.getId()).stream().map(this::projectSnapshot).toList());
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
                .orderByAsc(ResumeProject::getSort)
                .orderByDesc(ResumeProject::getUpdatedAt));
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

    private void fillApplication(JobApplication app, JobApplicationSaveDTO dto) {
        app.setTargetJobId(dto == null ? null : dto.getTargetJobId());
        app.setResumeVersionId(dto == null ? null : dto.getResumeVersionId());
        app.setMatchReportId(dto == null ? null : dto.getMatchReportId());
        app.setCompanyName(dto == null ? null : dto.getCompanyName());
        app.setJobTitle(StringUtils.hasText(dto == null ? null : dto.getJobTitle()) ? dto.getJobTitle() : "Untitled Job");
        app.setSource(dto == null ? null : dto.getSource());
        app.setStatus(StringUtils.hasText(dto == null ? null : dto.getStatus()) ? dto.getStatus() : "SAVED");
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
        if (dto.getReview() != null) {
            return writeJson(dto.getReview());
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
}
