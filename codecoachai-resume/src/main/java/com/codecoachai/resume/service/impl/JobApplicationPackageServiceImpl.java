package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.ApplicationPackageActionExecuteDTO;
import com.codecoachai.resume.domain.dto.ApplicationPackageCreateApplicationDTO;
import com.codecoachai.resume.domain.dto.ApplicationPackageSaveDTO;
import com.codecoachai.resume.domain.dto.JobApplicationSaveDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeJobMatchDetail;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.vo.ApplicationPackageActionExecuteVO;
import com.codecoachai.resume.domain.vo.JobApplicationPackageListItemVO;
import com.codecoachai.resume.domain.vo.JobApplicationPackageVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchDetailMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.JobApplicationPackageService;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class JobApplicationPackageServiceImpl implements JobApplicationPackageService {

    private static final int MATCH_READY_SCORE = 75;
    private static final int MATCH_MIN_SCORE = 60;
    private static final int PROJECT_READY_SCORE = 60;
    private static final Set<String> PACKAGE_STATUSES = Set.of("DRAFT", "READY", "APPLIED", "ARCHIVED");
    private static final String INVALID_PACKAGE_STATUS_FILTER = "__INVALID_STATUS__";

    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeJobMatchReportMapper resumeJobMatchReportMapper;
    private final ResumeJobMatchDetailMapper resumeJobMatchDetailMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final V4ResumeCareerService v4ResumeCareerService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public JobApplicationPackageVO preview(Long targetJobId, Long jdAnalysisId, Long resumeVersionId,
                                           Long matchReportId, List<Long> projectEvidenceIds) {
        Long userId = SecurityAssert.requireLoginUserId();
        PreviewContext context = loadContext(userId, targetJobId, jdAnalysisId, resumeVersionId, matchReportId,
                projectEvidenceIds);
        JobApplicationPackageVO vo = new JobApplicationPackageVO();
        vo.setId(previewId(context));
        vo.setUserId(userId);
        vo.setTargetJobId(context.targetJob == null ? context.targetJobId : context.targetJob.getId());
        vo.setJdAnalysisId(context.analysis == null ? context.jdAnalysisId : context.analysis.getId());
        vo.setRecommendedResumeVersionId(context.resumeVersion == null ? null : context.resumeVersion.getId());
        vo.setMatchReportId(context.report == null ? null : context.report.getId());
        vo.setProjectEvidenceIds(context.projectEvidence.stream().map(ProjectEvidence::getId).toList());
        vo.setCompanyName(firstText(
                context.targetJob == null ? null : context.targetJob.getCompanyName(),
                context.analysis == null ? null : context.analysis.getCompanyName(),
                context.report == null ? null : context.report.getSummary()));
        vo.setJobTitle(firstText(
                context.targetJob == null ? null : context.targetJob.getJobTitle(),
                context.analysis == null ? null : context.analysis.getJobTitle(),
                "目标岗位"));
        vo.setResultSource("MVP_RULE_BASED_PREVIEW");
        vo.setFallback(Boolean.FALSE);
        vo.setFallbackReason(null);
        vo.setSnapshotVersion(0);
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setEvidenceSources(buildEvidenceSources(context));
        vo.setRecommendedResume(buildRecommendedResume(context));
        vo.setMatchSummary(buildMatchSummary(context));
        vo.setProjectEvidenceCoverage(buildProjectCoverage(context));
        vo.setInterviewPreparation(buildInterviewPreparation(context, vo));
        vo.setChecklist(buildChecklist(context, vo));
        vo.setRiskSignals(buildRisks(context, vo));
        vo.setActions(buildActions(context, vo));
        vo.setSuggestions(buildSuggestions(context, vo));
        vo.setTrace(buildTrace(context, vo));
        applyReadiness(vo, context);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationVO createApplicationFromPreview(ApplicationPackageCreateApplicationDTO dto) {
        ApplicationPackageCreateApplicationDTO request = dto == null ? new ApplicationPackageCreateApplicationDTO() : dto;
        JobApplicationPackageVO preview = preview(request.getTargetJobId(), request.getJdAnalysisId(),
                request.getResumeVersionId(), request.getMatchReportId(), request.getProjectEvidenceIds());
        JobApplicationSaveDTO saveDTO = new JobApplicationSaveDTO();
        saveDTO.setTargetJobId(preview.getTargetJobId());
        saveDTO.setResumeVersionId(preview.getRecommendedResumeVersionId());
        saveDTO.setMatchReportId(preview.getMatchReportId());
        saveDTO.setCompanyName(firstText(request.getCompanyName(), preview.getCompanyName()));
        saveDTO.setJobTitle(firstText(request.getJobTitle(), preview.getJobTitle(), "Untitled Job"));
        saveDTO.setSource(firstText(request.getSource(), "APPLICATION_PACKAGE_PREVIEW"));
        saveDTO.setStatus(firstText(request.getStatus(), "SAVED"));
        saveDTO.setAppliedAt(request.getAppliedAt());
        saveDTO.setNextFollowUpAt(request.getNextFollowUpAt());
        saveDTO.setNote(applicationNote(request, preview));
        return v4ResumeCareerService.createApplication(saveDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationPackageVO save(ApplicationPackageSaveDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ApplicationPackageSaveDTO request = dto == null ? new ApplicationPackageSaveDTO() : dto;
        JobApplicationPackageVO snapshot = preview(request.getTargetJobId(), request.getJdAnalysisId(),
                request.getResumeVersionId(), request.getMatchReportId(), request.getProjectEvidenceIds());
        snapshot.setPackageNo(newPackageNo(userId));
        snapshot.setPackageStatus(firstText(normalizePackageStatusValue(request.getPackageStatus()), defaultPackageStatus(snapshot)));
        snapshot.setReadinessScore(readinessScore(snapshot));
        snapshot.setResultSource("REAL");
        snapshot.setFallback(Boolean.FALSE);
        snapshot.setFallbackReason(null);
        snapshot.setSnapshotVersion(1);
        snapshot.setRefreshedAt(LocalDateTime.now());

        Long packageId = insertPackage(userId, snapshot);
        snapshot.setId(String.valueOf(packageId));
        normalizePersistentActions(snapshot, packageId);
        updatePackageSnapshot(userId, packageId, snapshot, 1, snapshot.getRefreshedAt());
        writePackageEvent(userId, packageId, "PACKAGE_CREATED", "Application package saved", payloadOf(
                "targetJobId", snapshot.getTargetJobId(),
                "matchReportId", snapshot.getMatchReportId(),
                "readinessLevel", snapshot.getReadinessLevel()));
        return snapshot;
    }

    @Override
    public JobApplicationPackageVO detail(Long packageId) {
        Long userId = SecurityAssert.requireLoginUserId();
        PackageRow row = ownedPackage(userId, packageId);
        return toPackageDetail(row);
    }

    @Override
    public PageResult<JobApplicationPackageListItemVO> list(Long pageNo, Long pageSize, String status, String keyword) {
        Long userId = SecurityAssert.requireLoginUserId();
        long effectivePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        long effectivePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE user_id = ? AND deleted = 0");
        args.add(userId);
        String normalizedStatus = normalizePackageStatusFilter(status);
        if (StringUtils.hasText(normalizedStatus)) {
            where.append(" AND package_status = ?");
            args.add(normalizedStatus);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (company_name LIKE ? OR job_title LIKE ? OR package_no LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM job_application_package" + where,
                Long.class, args.toArray());
        if (total == null || total <= 0) {
            return PageResult.empty(effectivePageNo, effectivePageSize);
        }
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(effectivePageSize);
        queryArgs.add((effectivePageNo - 1) * effectivePageSize);
        List<JobApplicationPackageListItemVO> records = jdbcTemplate.query("""
                        SELECT id, package_no, target_job_id, jd_analysis_id, resume_version_id, match_report_id,
                               application_id, company_name, job_title, readiness_level, readiness_score,
                               readiness_reason, package_status, result_source, fallback, trace_id,
                               snapshot_version, refreshed_at, created_at, updated_at
                        FROM job_application_package
                        """ + where + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> toListItem(rs), queryArgs.toArray());
        return PageResult.of(records, total, effectivePageNo, effectivePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationPackageVO refresh(Long packageId) {
        Long userId = SecurityAssert.requireLoginUserId();
        PackageRow row = ownedPackage(userId, packageId);
        JobApplicationPackageVO snapshot = preview(row.targetJobId, row.jdAnalysisId, row.resumeVersionId,
                row.matchReportId, readLongList(row.projectEvidenceIdsJson));
        snapshot.setId(String.valueOf(row.id));
        snapshot.setPackageNo(row.packageNo);
        snapshot.setJobApplicationId(row.applicationId);
        snapshot.setPackageStatus(firstText(normalizePackageStatusValue(row.packageStatus), defaultPackageStatus(snapshot)));
        snapshot.setReadinessScore(readinessScore(snapshot));
        snapshot.setResultSource("REAL");
        snapshot.setFallback(Boolean.FALSE);
        snapshot.setFallbackReason(null);
        snapshot.setSnapshotVersion((row.snapshotVersion == null ? 0 : row.snapshotVersion) + 1);
        snapshot.setRefreshedAt(LocalDateTime.now());
        normalizePersistentActions(snapshot, row.id);
        updatePackageSnapshot(userId, row.id, snapshot, snapshot.getSnapshotVersion(), snapshot.getRefreshedAt());
        writePackageEvent(userId, row.id, "PACKAGE_REFRESHED", "Application package refreshed", payloadOf(
                "snapshotVersion", snapshot.getSnapshotVersion(),
                "readinessLevel", snapshot.getReadinessLevel()));
        return snapshot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationPackageActionExecuteVO executeAction(Long packageId, String actionCode,
                                                           ApplicationPackageActionExecuteDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        PackageRow row = ownedPackage(userId, packageId);
        JobApplicationPackageVO detail = toPackageDetail(row);
        String normalizedActionCode = normalizeActionCode(actionCode);
        JobApplicationPackageVO.CareerActionItemVO action = findAction(detail, normalizedActionCode);
        String actionType = normalizeActionCode(action == null ? normalizedActionCode : action.getActionType());
        if (!knownActionType(actionType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported application package action");
        }
        ApplicationPackageActionExecuteVO result = baseActionResult(row.id, normalizedActionCode, actionType);
        if ("CREATE_APPLICATION_RECORD".equalsIgnoreCase(actionType) || "CREATE_APPLICATION".equalsIgnoreCase(actionType)) {
            JobApplicationVO application = createApplicationFromPackage(row, detail, dto);
            jdbcTemplate.update("""
                            UPDATE job_application_package
                            SET application_id = ?, updated_at = ?
                            WHERE id = ? AND user_id = ? AND deleted = 0
                            """,
                    application.getId(), LocalDateTime.now(), row.id, userId);
            writePackageEvent(userId, row.id, "APPLICATION_CREATED", "Created application record from package", payloadOf(
                    "actionCode", normalizedActionCode,
                    "actionType", actionType,
                    "relatedBizType", "JOB_APPLICATION",
                    "relatedBizId", application.getId(),
                    "applicationId", application.getId()));
            result.setStatus("EXECUTED");
            result.setMessage("Application record is ready. No external delivery was sent.");
            result.setRelatedBizType("JOB_APPLICATION");
            result.setRelatedBizId(application.getId());
            result.setActionUrl("/applications?applicationId=" + application.getId() + "&openEvents=1");
            result.getPayload().put("application", application);
            result.setPackageDetail(detail(row.id));
            return result;
        }
        result.setStatus("CONTRACT_READY");
        result.setMessage("Action contract generated. The backend did not send external messages or auto-apply.");
        result.setActionUrl(action == null ? defaultActionUrl(actionType, detail) : action.getActionUrl());
        result.getPayload().putAll(actionPayload(actionType, detail));
        writePackageEvent(userId, row.id, "ACTION_CONTRACT_GENERATED", "Generated package action contract", payloadOf(
                "actionCode", normalizedActionCode,
                "actionType", actionType));
        result.setPackageDetail(detail);
        return result;
    }

    private Long insertPackage(Long userId, JobApplicationPackageVO snapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO job_application_package (
                        user_id, package_no, target_job_id, jd_analysis_id, resume_id, resume_version_id,
                        match_report_id, application_id, company_name, job_title, readiness_level,
                        readiness_score, readiness_reason, package_status, snapshot_json, checklist_json,
                        actions_json, project_evidence_ids_json, trace_id, result_source, fallback,
                        fallback_reason, snapshot_version, refreshed_at, created_at, updated_at, deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, userId);
            ps.setString(i++, snapshot.getPackageNo());
            ps.setObject(i++, snapshot.getTargetJobId());
            ps.setObject(i++, snapshot.getJdAnalysisId());
            ps.setObject(i++, snapshot.getRecommendedResume() == null ? null : snapshot.getRecommendedResume().getResumeId());
            ps.setObject(i++, snapshot.getRecommendedResumeVersionId());
            ps.setObject(i++, snapshot.getMatchReportId());
            ps.setObject(i++, snapshot.getJobApplicationId());
            ps.setString(i++, snapshot.getCompanyName());
            ps.setString(i++, snapshot.getJobTitle());
            ps.setString(i++, snapshot.getReadinessLevel());
            ps.setObject(i++, snapshot.getReadinessScore());
            ps.setString(i++, snapshot.getReadinessReason());
            ps.setString(i++, snapshot.getPackageStatus());
            ps.setString(i++, writeJson(snapshot));
            ps.setString(i++, writeJson(snapshot.getChecklist()));
            ps.setString(i++, writeJson(snapshot.getActions()));
            ps.setString(i++, writeJson(snapshot.getProjectEvidenceIds()));
            ps.setString(i++, snapshot.getTrace() == null ? null : snapshot.getTrace().getTraceId());
            ps.setString(i++, snapshot.getResultSource());
            ps.setObject(i++, Boolean.TRUE.equals(snapshot.getFallback()) ? 1 : 0);
            ps.setString(i++, snapshot.getFallbackReason());
            ps.setObject(i++, snapshot.getSnapshotVersion());
            ps.setTimestamp(i++, toTimestamp(snapshot.getRefreshedAt()));
            ps.setTimestamp(i++, toTimestamp(now));
            ps.setTimestamp(i, toTimestamp(now));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to save application package");
        }
        return key.longValue();
    }

    private void updatePackageSnapshot(Long userId, Long packageId, JobApplicationPackageVO snapshot,
                                       Integer snapshotVersion, LocalDateTime refreshedAt) {
        jdbcTemplate.update("""
                        UPDATE job_application_package
                        SET target_job_id = ?, jd_analysis_id = ?, resume_id = ?, resume_version_id = ?,
                            match_report_id = ?, company_name = ?, job_title = ?, readiness_level = ?,
                            readiness_score = ?, readiness_reason = ?, package_status = ?, snapshot_json = ?,
                            checklist_json = ?, actions_json = ?, project_evidence_ids_json = ?, trace_id = ?,
                            result_source = ?, fallback = ?, fallback_reason = ?, snapshot_version = ?,
                            refreshed_at = ?, updated_at = ?
                        WHERE id = ? AND user_id = ? AND deleted = 0
                        """,
                snapshot.getTargetJobId(),
                snapshot.getJdAnalysisId(),
                snapshot.getRecommendedResume() == null ? null : snapshot.getRecommendedResume().getResumeId(),
                snapshot.getRecommendedResumeVersionId(),
                snapshot.getMatchReportId(),
                snapshot.getCompanyName(),
                snapshot.getJobTitle(),
                snapshot.getReadinessLevel(),
                snapshot.getReadinessScore(),
                snapshot.getReadinessReason(),
                snapshot.getPackageStatus(),
                writeJson(snapshot),
                writeJson(snapshot.getChecklist()),
                writeJson(snapshot.getActions()),
                writeJson(snapshot.getProjectEvidenceIds()),
                snapshot.getTrace() == null ? null : snapshot.getTrace().getTraceId(),
                snapshot.getResultSource(),
                Boolean.TRUE.equals(snapshot.getFallback()) ? 1 : 0,
                snapshot.getFallbackReason(),
                snapshotVersion,
                refreshedAt,
                LocalDateTime.now(),
                packageId,
                userId);
    }

    private PackageRow ownedPackage(Long userId, Long packageId) {
        if (userId == null || packageId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "packageId is required");
        }
        List<PackageRow> rows = jdbcTemplate.query("""
                        SELECT *
                        FROM job_application_package
                        WHERE id = ? AND user_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                (rs, rowNum) -> toPackageRow(rs), packageId, userId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Application package not found or no permission");
        }
        return rows.get(0);
    }

    private JobApplicationPackageVO toPackageDetail(PackageRow row) {
        JobApplicationPackageVO vo = readPackageSnapshot(row.snapshotJson);
        if (vo == null) {
            vo = new JobApplicationPackageVO();
            vo.setChecklist(readList(row.checklistJson, JobApplicationPackageVO.ApplicationPackageChecklistItemVO.class));
            vo.setActions(readList(row.actionsJson, JobApplicationPackageVO.CareerActionItemVO.class));
            vo.setProjectEvidenceIds(readLongList(row.projectEvidenceIdsJson));
        }
        vo.setId(String.valueOf(row.id));
        vo.setPackageNo(row.packageNo);
        vo.setUserId(row.userId);
        vo.setTargetJobId(row.targetJobId);
        vo.setJdAnalysisId(row.jdAnalysisId);
        vo.setRecommendedResumeVersionId(row.resumeVersionId);
        vo.setMatchReportId(row.matchReportId);
        vo.setJobApplicationId(row.applicationId);
        vo.setCompanyName(row.companyName);
        vo.setJobTitle(row.jobTitle);
        vo.setReadinessLevel(row.readinessLevel);
        vo.setReadinessScore(row.readinessScore);
        vo.setReadinessReason(row.readinessReason);
        vo.setPackageStatus(firstText(normalizePackageStatusValue(row.packageStatus), row.packageStatus));
        vo.setResultSource(row.resultSource);
        vo.setFallback(row.fallback);
        vo.setFallbackReason(row.fallbackReason);
        vo.setSnapshotVersion(row.snapshotVersion);
        vo.setRefreshedAt(row.refreshedAt);
        normalizePersistentActions(vo, row.id);
        return vo;
    }

    private JobApplicationPackageListItemVO toListItem(ResultSet rs) throws SQLException {
        JobApplicationPackageListItemVO vo = new JobApplicationPackageListItemVO();
        vo.setId(rs.getLong("id"));
        vo.setPackageNo(rs.getString("package_no"));
        vo.setTargetJobId(getLong(rs, "target_job_id"));
        vo.setJdAnalysisId(getLong(rs, "jd_analysis_id"));
        vo.setRecommendedResumeVersionId(getLong(rs, "resume_version_id"));
        vo.setMatchReportId(getLong(rs, "match_report_id"));
        vo.setJobApplicationId(getLong(rs, "application_id"));
        vo.setCompanyName(rs.getString("company_name"));
        vo.setJobTitle(rs.getString("job_title"));
        vo.setReadinessLevel(rs.getString("readiness_level"));
        vo.setReadinessScore(getInteger(rs, "readiness_score"));
        vo.setReadinessReason(rs.getString("readiness_reason"));
        String packageStatus = rs.getString("package_status");
        vo.setPackageStatus(firstText(normalizePackageStatusValue(packageStatus), packageStatus));
        vo.setResultSource(rs.getString("result_source"));
        vo.setFallback(getBoolean(rs, "fallback"));
        vo.setTraceId(rs.getString("trace_id"));
        vo.setSnapshotVersion(getInteger(rs, "snapshot_version"));
        vo.setRefreshedAt(getLocalDateTime(rs, "refreshed_at"));
        vo.setCreatedAt(getLocalDateTime(rs, "created_at"));
        vo.setUpdatedAt(getLocalDateTime(rs, "updated_at"));
        return vo;
    }

    private PackageRow toPackageRow(ResultSet rs) throws SQLException {
        PackageRow row = new PackageRow();
        row.id = rs.getLong("id");
        row.userId = getLong(rs, "user_id");
        row.packageNo = rs.getString("package_no");
        row.targetJobId = getLong(rs, "target_job_id");
        row.jdAnalysisId = getLong(rs, "jd_analysis_id");
        row.resumeVersionId = getLong(rs, "resume_version_id");
        row.matchReportId = getLong(rs, "match_report_id");
        row.applicationId = getLong(rs, "application_id");
        row.companyName = rs.getString("company_name");
        row.jobTitle = rs.getString("job_title");
        row.readinessLevel = rs.getString("readiness_level");
        row.readinessScore = getInteger(rs, "readiness_score");
        row.readinessReason = rs.getString("readiness_reason");
        row.packageStatus = rs.getString("package_status");
        row.snapshotJson = rs.getString("snapshot_json");
        row.checklistJson = rs.getString("checklist_json");
        row.actionsJson = rs.getString("actions_json");
        row.projectEvidenceIdsJson = rs.getString("project_evidence_ids_json");
        row.traceId = rs.getString("trace_id");
        row.resultSource = rs.getString("result_source");
        row.fallback = getBoolean(rs, "fallback");
        row.fallbackReason = rs.getString("fallback_reason");
        row.snapshotVersion = getInteger(rs, "snapshot_version");
        row.refreshedAt = getLocalDateTime(rs, "refreshed_at");
        return row;
    }

    private JobApplicationVO createApplicationFromPackage(PackageRow row, JobApplicationPackageVO detail,
                                                          ApplicationPackageActionExecuteDTO dto) {
        if (row.applicationId != null) {
            JobApplicationVO existing = new JobApplicationVO();
            existing.setId(row.applicationId);
            existing.setTargetJobId(row.targetJobId);
            existing.setResumeVersionId(row.resumeVersionId);
            existing.setMatchReportId(row.matchReportId);
            existing.setCompanyName(row.companyName);
            existing.setJobTitle(row.jobTitle);
            existing.setStatus("EXISTING");
            return existing;
        }
        JobApplicationSaveDTO saveDTO = new JobApplicationSaveDTO();
        saveDTO.setTargetJobId(row.targetJobId);
        saveDTO.setResumeVersionId(row.resumeVersionId);
        saveDTO.setMatchReportId(row.matchReportId);
        saveDTO.setCompanyName(firstText(dto == null ? null : stringPayload(dto, "companyName"), row.companyName));
        saveDTO.setJobTitle(firstText(dto == null ? null : stringPayload(dto, "jobTitle"), row.jobTitle, "Untitled Job"));
        saveDTO.setSource(firstText(dto == null ? null : dto.getSource(), "APPLICATION_PACKAGE"));
        saveDTO.setStatus(firstText(dto == null ? null : dto.getStatus(), "SAVED"));
        saveDTO.setAppliedAt(dto == null ? null : dto.getAppliedAt());
        saveDTO.setNextFollowUpAt(dto == null ? null : dto.getNextFollowUpAt());
        saveDTO.setNote(packageApplicationNote(row, detail, dto));
        return v4ResumeCareerService.createApplication(saveDTO);
    }

    private ApplicationPackageActionExecuteVO baseActionResult(Long packageId, String actionCode, String actionType) {
        ApplicationPackageActionExecuteVO vo = new ApplicationPackageActionExecuteVO();
        vo.setPackageId(packageId);
        vo.setActionCode(actionCode);
        vo.setActionType(actionType);
        return vo;
    }

    private JobApplicationPackageVO.CareerActionItemVO findAction(JobApplicationPackageVO detail, String actionCode) {
        if (detail == null || detail.getActions() == null || !StringUtils.hasText(actionCode)) {
            return null;
        }
        String normalized = normalizeActionCode(actionCode);
        return detail.getActions().stream()
                .filter(action -> normalized.equalsIgnoreCase(normalizeActionCode(action.getId()))
                        || normalized.equalsIgnoreCase(normalizeActionCode(action.getActionType())))
                .findFirst()
                .orElse(null);
    }

    private boolean knownActionType(String actionType) {
        String normalized = normalizeActionCode(actionType);
        return "CREATE_APPLICATION_RECORD".equalsIgnoreCase(normalized)
                || "PRACTICE_INTERVIEW".equalsIgnoreCase(normalized)
                || "SET_FOLLOW_UP".equalsIgnoreCase(normalized)
                || "UPDATE_RESUME_VERSION".equalsIgnoreCase(normalized)
                || "ADD_PROJECT_EVIDENCE".equalsIgnoreCase(normalized);
    }

    private String normalizeActionCode(String actionCode) {
        String normalized = actionCode == null ? "" : actionCode.trim().toUpperCase();
        if ("CREATE_APPLICATION".equals(normalized) || "CREATE-APPLICATION".equals(normalized)) {
            return "CREATE_APPLICATION_RECORD";
        }
        if ("SET_FOLLOW_UP_PLAN".equals(normalized) || "SET-FOLLOW-UP".equals(normalized)) {
            return "SET_FOLLOW_UP";
        }
        if ("PRACTICE-INTERVIEW".equals(normalized)) {
            return "PRACTICE_INTERVIEW";
        }
        if ("ADD-PROJECT-EVIDENCE".equals(normalized)) {
            return "ADD_PROJECT_EVIDENCE";
        }
        if ("UPDATE-RESUME".equals(normalized) || "UPDATE-RESUME-VERSION".equals(normalized)) {
            return "UPDATE_RESUME_VERSION";
        }
        return normalized;
    }

    private Map<String, Object> actionPayload(String actionType, JobApplicationPackageVO detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if ("PRACTICE_INTERVIEW".equalsIgnoreCase(actionType)) {
            payload.put("mode", "COMPREHENSIVE");
            payload.put("interviewMode", "COMPREHENSIVE");
            payload.put("basedOnResume", Boolean.TRUE);
            payload.put("applicationPackageId", detail.getId());
            payload.put("applicationId", detail.getJobApplicationId());
            payload.put("targetJobId", detail.getTargetJobId());
            payload.put("jdAnalysisId", detail.getJdAnalysisId());
            payload.put("resumeVersionId", detail.getRecommendedResumeVersionId());
            payload.put("matchReportId", detail.getMatchReportId());
            payload.put("projectEvidenceIds", detail.getProjectEvidenceIds());
            payload.put("targetPosition", detail.getJobTitle());
            payload.put("recommendationSource", "APPLICATION_PACKAGE");
            payload.put("recommendationReason", detail.getReadinessReason());
            payload.put("trainingScene", "APPLICATION_PACKAGE_PREP");
        } else if ("SET_FOLLOW_UP".equalsIgnoreCase(actionType)) {
            payload.put("applicationId", detail.getJobApplicationId());
            payload.put("sourceType", "APPLICATION_PACKAGE");
            payload.put("sourceId", detail.getId());
        } else {
            payload.put("sourceType", "APPLICATION_PACKAGE");
            payload.put("sourceId", detail.getId());
            payload.put("targetJobId", detail.getTargetJobId());
            payload.put("resumeVersionId", detail.getRecommendedResumeVersionId());
            payload.put("matchReportId", detail.getMatchReportId());
        }
        return payload;
    }

    private String defaultActionUrl(String actionType, JobApplicationPackageVO detail) {
        if ("PRACTICE_INTERVIEW".equalsIgnoreCase(actionType)) {
            return "/interviews/create";
        }
        if ("ADD_PROJECT_EVIDENCE".equalsIgnoreCase(actionType)) {
            return projectEvidenceUrl(detail.getTargetJobId());
        }
        if ("UPDATE_RESUME_VERSION".equalsIgnoreCase(actionType)) {
            return matchUrl(detail.getMatchReportId());
        }
        if ("SET_FOLLOW_UP".equalsIgnoreCase(actionType) && detail.getJobApplicationId() != null) {
            return "/applications?applicationId=" + detail.getJobApplicationId() + "&openEvents=1";
        }
        return "/applications";
    }

    private void writePackageEvent(Long userId, Long packageId, String eventType, String summary,
                                   Map<String, Object> payload) {
        String actionCode = payload == null || payload.get("actionCode") == null ? null : String.valueOf(payload.get("actionCode"));
        String relatedBizType = payload == null || payload.get("relatedBizType") == null ? null : String.valueOf(payload.get("relatedBizType"));
        Long relatedBizId = payload == null ? null : toLong(payload.get("relatedBizId"));
        PackageRow row = ownedPackage(userId, packageId);
        if (!StringUtils.hasText(relatedBizType)) {
            relatedBizType = "APPLICATION_PACKAGE";
            relatedBizId = packageId;
        }
        jdbcTemplate.update("""
                        INSERT INTO job_application_package_event (
                            package_id, user_id, event_type, event_time, action_code, related_biz_type,
                            related_biz_id, summary, event_payload_json, trace_id, result_source, fallback,
                            fallback_reason, snapshot_version, created_at, updated_at, deleted
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                packageId, userId, eventType, LocalDateTime.now(), actionCode, relatedBizType, relatedBizId,
                summary, writeJson(payload), row.traceId, row.resultSource,
                Boolean.TRUE.equals(row.fallback) ? 1 : 0, row.fallbackReason, row.snapshotVersion,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private PreviewContext loadContext(Long userId, Long targetJobId, Long jdAnalysisId, Long resumeVersionId,
                                       Long matchReportId, List<Long> projectEvidenceIds) {
        PreviewContext context = new PreviewContext();
        context.targetJobId = targetJobId;
        context.jdAnalysisId = jdAnalysisId;
        context.resumeVersionId = resumeVersionId;
        context.matchReportId = matchReportId;

        context.report = ownedReport(userId, matchReportId);
        if (context.report != null) {
            context.targetJobId = firstLong(context.targetJobId, context.report.getTargetJobId());
            context.jdAnalysisId = firstLong(context.jdAnalysisId, context.report.getJdAnalysisId());
            context.resumeVersionId = firstLong(context.resumeVersionId, context.report.getResumeVersionId());
        }
        context.targetJob = ownedTargetJob(userId, context.targetJobId);
        context.analysis = ownedAnalysis(userId, context.jdAnalysisId);
        if (context.analysis == null && context.targetJob != null) {
            context.analysis = latestAnalysis(userId, context.targetJob.getId());
        }
        if (context.targetJob == null && context.analysis != null) {
            context.targetJobId = firstLong(context.targetJobId, context.analysis.getTargetJobId());
            context.targetJob = ownedTargetJob(userId, context.targetJobId);
        }
        context.resumeVersion = ownedResumeVersion(userId, context.resumeVersionId);
        if (context.report == null) {
            context.report = latestReport(userId,
                    context.targetJob == null ? context.targetJobId : context.targetJob.getId(),
                    context.resumeVersion == null ? null : context.resumeVersion.getId());
        }
        if (context.resumeVersion == null && context.report != null) {
            context.resumeVersion = ownedResumeVersion(userId, context.report.getResumeVersionId());
        }
        if (context.resumeVersion == null) {
            context.resumeVersion = latestResumeVersion(userId);
        }
        context.matchDetails = listMatchDetails(userId, context.report);
        context.projectEvidence = listProjectEvidence(userId,
                context.targetJob == null ? context.targetJobId : context.targetJob.getId(),
                projectEvidenceIds);
        return context;
    }

    private TargetJob ownedTargetJob(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return null;
        }
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "目标岗位不存在或无权访问");
        }
        return targetJob;
    }

    private JobDescriptionAnalysis ownedAnalysis(Long userId, Long jdAnalysisId) {
        if (jdAnalysisId == null) {
            return null;
        }
        JobDescriptionAnalysis analysis = jobDescriptionAnalysisMapper.selectOne(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getId, jdAnalysisId)
                .eq(JobDescriptionAnalysis::getUserId, userId)
                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (analysis == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "JD 分析不存在或无权访问");
        }
        return analysis;
    }

    private JobDescriptionAnalysis latestAnalysis(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return null;
        }
        return jobDescriptionAnalysisMapper.selectOne(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getUserId, userId)
                .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                .last("limit 1"));
    }

    private ResumeVersion ownedResumeVersion(Long userId, Long resumeVersionId) {
        if (resumeVersionId == null) {
            return null;
        }
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, resumeVersionId)
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (version == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历版本不存在或无权访问");
        }
        return version;
    }

    private ResumeVersion latestResumeVersion(Long userId) {
        return resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeVersion::getCurrentFlag)
                .orderByDesc(ResumeVersion::getUpdatedAt)
                .last("limit 1"));
    }

    private ResumeJobMatchReport ownedReport(Long userId, Long matchReportId) {
        if (matchReportId == null) {
            return null;
        }
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

    private ResumeJobMatchReport latestReport(Long userId, Long targetJobId, Long resumeVersionId) {
        if (targetJobId == null) {
            return null;
        }
        LambdaQueryWrapper<ResumeJobMatchReport> wrapper = new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getTargetJobId, targetJobId)
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeJobMatchReport::getUpdatedAt)
                .last("limit 1");
        if (resumeVersionId != null) {
            wrapper.eq(ResumeJobMatchReport::getResumeVersionId, resumeVersionId);
        }
        return resumeJobMatchReportMapper.selectOne(wrapper);
    }

    private List<ResumeJobMatchDetail> listMatchDetails(Long userId, ResumeJobMatchReport report) {
        if (report == null || report.getId() == null) {
            return List.of();
        }
        List<ResumeJobMatchDetail> details = resumeJobMatchDetailMapper.selectList(new LambdaQueryWrapper<ResumeJobMatchDetail>()
                .eq(ResumeJobMatchDetail::getUserId, userId)
                .eq(ResumeJobMatchDetail::getReportId, report.getId())
                .eq(ResumeJobMatchDetail::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeJobMatchDetail::getId));
        return details == null ? List.of() : details;
    }

    private List<ProjectEvidence> listProjectEvidence(Long userId, Long targetJobId, List<Long> projectEvidenceIds) {
        LambdaQueryWrapper<ProjectEvidence> wrapper = new LambdaQueryWrapper<ProjectEvidence>()
                .eq(ProjectEvidence::getUserId, userId)
                .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                .orderByDesc(ProjectEvidence::getCompletenessScore)
                .orderByDesc(ProjectEvidence::getUpdatedAt)
                .last("limit 5");
        if (!CollectionUtils.isEmpty(projectEvidenceIds)) {
            wrapper.in(ProjectEvidence::getId, projectEvidenceIds);
        } else if (targetJobId != null) {
            wrapper.eq(ProjectEvidence::getTargetJobId, targetJobId);
        } else {
            return List.of();
        }
        List<ProjectEvidence> evidence = projectEvidenceMapper.selectList(wrapper);
        return evidence == null ? List.of() : evidence;
    }

    private List<JobApplicationPackageVO.EvidenceSourceVO> buildEvidenceSources(PreviewContext context) {
        List<JobApplicationPackageVO.EvidenceSourceVO> sources = new ArrayList<>();
        if (context.targetJob != null || context.analysis != null) {
            sources.add(evidenceSource("jd", "JD_ANALYSIS",
                    String.valueOf(context.analysis == null ? context.targetJob.getId() : context.analysis.getId()),
                    firstText(context.targetJob == null ? null : context.targetJob.getJobTitle(),
                            context.analysis == null ? null : context.analysis.getJobTitle(), "JD"),
                    firstText(context.analysis == null ? null : context.analysis.getSummary(),
                            context.targetJob == null ? null : context.targetJob.getJdSource(), "目标岗位/JD 信息"),
                    context.analysis == null ? "LOW" : "HIGH"));
        }
        if (context.resumeVersion != null) {
            sources.add(evidenceSource("resume", "RESUME_VERSION", String.valueOf(context.resumeVersion.getId()),
                    firstText(context.resumeVersion.getVersionName(), "简历版本"),
                    "用于本次投递包的推荐简历版本", "MEDIUM"));
        }
        if (context.report != null) {
            sources.add(evidenceSource("match", "RESUME_JOB_MATCH_REPORT", String.valueOf(context.report.getId()),
                    "简历/JD 匹配报告",
                    firstText(context.report.getSummary(), "匹配报告用于判断简历适配度和风险"), "HIGH"));
        }
        for (ProjectEvidence evidence : context.projectEvidence) {
            sources.add(evidenceSource("project:" + evidence.getId(), "PROJECT_EVIDENCE",
                    String.valueOf(evidence.getId()), firstText(evidence.getTitle(), "项目证据"),
                    firstText(evidence.getResult(), evidence.getSolution(), evidence.getResponsibility(), "项目证据摘要"),
                    evidenceCompletenessReady(evidence) ? "MEDIUM" : "LOW"));
        }
        return sources;
    }

    private JobApplicationPackageVO.RecommendedResumeVO buildRecommendedResume(PreviewContext context) {
        if (context.resumeVersion == null) {
            return null;
        }
        JobApplicationPackageVO.RecommendedResumeVO vo = new JobApplicationPackageVO.RecommendedResumeVO();
        vo.setResumeVersionId(context.resumeVersion.getId());
        vo.setResumeId(context.resumeVersion.getResumeId());
        vo.setVersionNo(context.resumeVersion.getVersionNo());
        vo.setVersionName(context.resumeVersion.getVersionName());
        vo.setCurrentFlag(context.resumeVersion.getCurrentFlag());
        vo.setReason(context.report != null
                ? "该版本已关联当前岗位的匹配报告，优先作为本次投递包主简历。"
                : "暂未找到当前岗位匹配报告，先使用当前可用简历版本作为预览候选。");
        return vo;
    }

    private JobApplicationPackageVO.MatchSummaryVO buildMatchSummary(PreviewContext context) {
        JobApplicationPackageVO.MatchSummaryVO vo = new JobApplicationPackageVO.MatchSummaryVO();
        if (context.report == null) {
            vo.setStatus("MISSING");
            vo.setSummary("暂无简历/JD 匹配报告。");
            return vo;
        }
        vo.setOverallScore(context.report.getOverallScore());
        vo.setTechStackScore(context.report.getTechStackScore());
        vo.setProjectExperienceScore(context.report.getProjectExperienceScore());
        vo.setBusinessFitScore(context.report.getBusinessFitScore());
        vo.setCommunicationScore(context.report.getCommunicationScore());
        vo.setStatus(context.report.getStatus());
        vo.setSummary(context.report.getSummary());
        vo.setGaps(jsonTexts(context.report.getGapsJson(), 5));
        vo.setInterviewTopics(firstNonEmpty(jsonTexts(context.report.getRecommendedInterviewTopicsJson(), 5),
                context.analysis == null ? List.of() : jsonTexts(context.analysis.getInterviewFocusJson(), 5)));
        return vo;
    }

    private JobApplicationPackageVO.ProjectEvidenceCoverageVO buildProjectCoverage(PreviewContext context) {
        JobApplicationPackageVO.ProjectEvidenceCoverageVO vo = new JobApplicationPackageVO.ProjectEvidenceCoverageVO();
        for (ResumeJobMatchDetail detail : context.matchDetails) {
            String requirement = firstText(detail.getSkillName(), detail.getDimension(), "岗位要求");
            if (detailEvidenceReady(detail)) {
                addUnique(vo.getCoveredRequirements(), requirement);
            } else if (StringUtils.hasText(detail.getGapDescription()) || scoreBelow(detail.getScore(), MATCH_READY_SCORE)) {
                addUnique(vo.getInsufficientRequirements(), requirement);
            }
        }
        if (vo.getCoveredRequirements().isEmpty() && context.analysis != null) {
            List<String> requirements = jsonTexts(context.analysis.getRequiredSkillsJson(), 5);
            if (hasReadyProjectEvidence(context.projectEvidence)) {
                requirements.stream().limit(3).forEach(item -> addUnique(vo.getCoveredRequirements(), item));
            } else {
                requirements.stream().limit(3).forEach(item -> addUnique(vo.getInsufficientRequirements(), item));
            }
        }
        if (vo.getInsufficientRequirements().isEmpty() && !hasReadyProjectEvidence(context.projectEvidence)) {
            addUnique(vo.getInsufficientRequirements(), "至少一段可用于面试深挖的项目证据");
        }
        vo.setSuggestedFields(suggestedProjectFields(context.projectEvidence));
        vo.setSelectedEvidence(context.projectEvidence.stream().map(this::toProjectEvidenceSummary).toList());
        return vo;
    }

    private JobApplicationPackageVO.InterviewPreparationVO buildInterviewPreparation(PreviewContext context,
                                                                                     JobApplicationPackageVO packageVO) {
        JobApplicationPackageVO.InterviewPreparationVO vo = new JobApplicationPackageVO.InterviewPreparationVO();
        List<String> topics = firstNonEmpty(
                context.report == null ? List.of() : jsonTexts(context.report.getRecommendedInterviewTopicsJson(), 5),
                context.analysis == null ? List.of() : jsonTexts(context.analysis.getInterviewFocusJson(), 5));
        if (topics.isEmpty()) {
            topics = List.of("围绕 JD 核心要求做一次文本模拟面试", "准备项目证据中的职责、难点、结果和复盘");
        }
        vo.setTopics(topics);
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("targetJobId", packageVO.getTargetJobId());
        params.put("matchReportId", packageVO.getMatchReportId());
        params.put("resumeVersionId", packageVO.getRecommendedResumeVersionId());
        params.put("projectEvidenceIds", packageVO.getProjectEvidenceIds());
        vo.setCreateParams(params);
        String query = "targetJobId=" + nullSafe(packageVO.getTargetJobId())
                + "&matchReportId=" + nullSafe(packageVO.getMatchReportId())
                + "&resumeVersionId=" + nullSafe(packageVO.getRecommendedResumeVersionId());
        vo.setEntryUrl("/interviews/create?" + query);
        return vo;
    }

    private List<JobApplicationPackageVO.ApplicationPackageChecklistItemVO> buildChecklist(PreviewContext context,
                                                                                           JobApplicationPackageVO vo) {
        List<JobApplicationPackageVO.ApplicationPackageChecklistItemVO> items = new ArrayList<>();
        boolean jdParsed = context.analysis != null
                && JobDescriptionParseStatus.PARSED.getCode().equals(context.analysis.getParseStatus());
        items.add(check("JD_PARSED", "JD 已解析", jdParsed,
                jdParsed ? "已找到可用 JD 分析。" : "缺少已解析 JD，建议先完成岗位解析。",
                "BLOCKER", "PARSE_JD", targetJobUrl(vo.getTargetJobId()), List.of("jd")));
        items.add(check("RESUME_AVAILABLE", "有可用简历版本", context.resumeVersion != null,
                context.resumeVersion == null ? "暂未找到可用于该岗位的简历版本。" : "已选定推荐简历版本。",
                "BLOCKER", "UPDATE_RESUME_VERSION", "/resume-versions", List.of("resume")));
        boolean recommendationExplained = context.resumeVersion != null;
        items.add(check("RESUME_RECOMMENDATION_EXPLAINED", "推荐简历版本有解释", recommendationExplained,
                recommendationExplained ? vo.getRecommendedResume().getReason() : "需要先选择或生成简历版本。",
                "WARN", "UPDATE_RESUME_VERSION", "/resume-versions", List.of("resume", "match")));
        boolean matchReady = matchSuccess(context.report) && scoreAtLeast(context.report.getOverallScore(), MATCH_READY_SCORE);
        items.add(check("MATCH_SCORE_THRESHOLD", "简历/JD 匹配分达到最低阈值", matchReady,
                matchReady ? "匹配分达到投递前阈值。" : "匹配报告缺失或分数不足，建议补简历或重新匹配。",
                "WARN", "UPDATE_RESUME_VERSION", matchUrl(vo.getMatchReportId()), List.of("match")));
        boolean skillEvidence = !vo.getProjectEvidenceCoverage().getCoveredRequirements().isEmpty();
        items.add(check("SKILL_EVIDENCE_COVERED", "核心技能要求有证据支撑", skillEvidence,
                skillEvidence ? "已有要求可被匹配报告或项目证据支撑。" : "核心技能证据不足，建议补充项目证据。",
                "WARN", "ADD_PROJECT_EVIDENCE", projectEvidenceUrl(vo.getTargetJobId()), List.of("match")));
        boolean projectReady = hasReadyProjectEvidence(context.projectEvidence);
        items.add(check("PROJECT_EVIDENCE_READY", "至少一段项目证据可用于面试深挖", projectReady,
                projectReady ? "已找到可用项目证据。" : "项目证据不足，建议补充职责、难点、方案和结果。",
                "WARN", "ADD_PROJECT_EVIDENCE", projectEvidenceUrl(vo.getTargetJobId()), projectEvidenceSourceIds(context)));
        boolean interviewReady = vo.getInterviewPreparation() != null && !vo.getInterviewPreparation().getTopics().isEmpty();
        items.add(check("INTERVIEW_PREPARATION_READY", "已生成面试准备方向", interviewReady,
                interviewReady ? "已生成可带上下文进入模拟面试的准备方向。" : "建议先生成面试准备方向。",
                "INFO", "PRACTICE_INTERVIEW", "/interviews/create", List.of("match", "jd")));
        items.add(check("FOLLOW_UP_PLAN_READY", "已设置投递后跟进计划", false,
                "预览阶段不会自动设置跟进日期，创建投递记录时请填写下一次跟进时间。",
                "INFO", "SET_FOLLOW_UP", "/applications", List.of()));
        return items;
    }

    private List<JobApplicationPackageVO.CareerRiskSignalVO> buildRisks(PreviewContext context,
                                                                         JobApplicationPackageVO vo) {
        List<JobApplicationPackageVO.CareerRiskSignalVO> risks = new ArrayList<>();
        if (context.analysis == null) {
            risks.add(risk("JD_NOT_PARSED", "HIGH", "JD 信息不足", "缺少已解析 JD，投递包只能给出补资料行动。", List.of("jd")));
        }
        if (!matchSuccess(context.report)) {
            risks.add(risk("MATCH_REPORT_MISSING", "MEDIUM", "缺少可信匹配报告", "尚无成功匹配报告，简历推荐只能按可用版本降级处理。", List.of("resume")));
        } else if (scoreBelow(context.report.getOverallScore(), MATCH_READY_SCORE)) {
            risks.add(risk("MATCH_SCORE_LOW", "MEDIUM", "简历匹配仍有缺口", "当前匹配分未达到 READY 阈值，建议先优化简历或补证据。", List.of("match")));
        }
        if (!hasReadyProjectEvidence(context.projectEvidence)) {
            risks.add(risk("PROJECT_EVIDENCE_WEAK", "MEDIUM", "项目证据不足", "缺少完整项目证据时，不应把投递建议包装成强结论。", projectEvidenceSourceIds(context)));
        }
        if (!Boolean.TRUE.equals(findChecklist(vo, "FOLLOW_UP_PLAN_READY"))) {
            risks.add(risk("FOLLOW_UP_NOT_SET", "LOW", "跟进计划未设置", "创建投递记录时需要设置下一次跟进，避免投递后断链。", List.of()));
        }
        return risks;
    }

    private List<JobApplicationPackageVO.CareerActionItemVO> buildActions(PreviewContext context,
                                                                          JobApplicationPackageVO vo) {
        List<JobApplicationPackageVO.CareerActionItemVO> actions = new ArrayList<>();
        if (context.resumeVersion == null || !matchSuccess(context.report)
                || scoreBelow(context.report == null ? null : context.report.getOverallScore(), MATCH_READY_SCORE)) {
            actions.add(action("update-resume", "UPDATE_RESUME_VERSION", "补齐或优化简历版本",
                    "先让简历版本和当前 JD 形成可信匹配报告，再决定是否投递。",
                    "HIGH", matchUrl(vo.getMatchReportId()), "APPLICATION_PACKAGE", vo.getId(), List.of("resume", "match")));
        }
        if (!hasReadyProjectEvidence(context.projectEvidence)) {
            actions.add(action("add-project-evidence", "ADD_PROJECT_EVIDENCE", "补充项目证据",
                    "补齐项目职责、技术难点、解决方案、结果指标和复盘字段。",
                    "HIGH", projectEvidenceUrl(vo.getTargetJobId()), "APPLICATION_PACKAGE", vo.getId(), projectEvidenceSourceIds(context)));
        }
        if (scoreBelow(context.report == null ? null : context.report.getOverallScore(), 85)) {
            actions.add(action("practice-interview", "PRACTICE_INTERVIEW", "带岗位上下文练一场模拟面试",
                    "围绕匹配报告缺口和项目证据进行文本模拟面试。",
                    "MEDIUM", vo.getInterviewPreparation().getEntryUrl(), "APPLICATION_PACKAGE", vo.getId(), List.of("jd", "match")));
        }
        actions.add(action("create-application", "CREATE_APPLICATION_RECORD", "创建投递记录",
                "只在系统内创建个人投递记录，不会自动投递真实岗位。",
                "MEDIUM", "/applications", "APPLICATION_PACKAGE", vo.getId(), List.of("jd", "resume", "match")));
        actions.add(action("set-follow-up", "SET_FOLLOW_UP", "设置投递后跟进时间",
                "创建记录时设置下一次跟进，后续可进入投递漏斗和今日行动。",
                "LOW", "/applications", "APPLICATION_PACKAGE", vo.getId(), List.of()));
        return actions;
    }

    private List<JobApplicationPackageVO.ExplainableSuggestionVO> buildSuggestions(PreviewContext context,
                                                                                   JobApplicationPackageVO vo) {
        List<JobApplicationPackageVO.ExplainableSuggestionVO> suggestions = new ArrayList<>();
        if (context.resumeVersion != null) {
            suggestions.add(suggestion("resume", "RESUME_VERSION", "推荐使用当前简历版本",
                    vo.getRecommendedResume().getReason(), context.report == null ? "MEDIUM" : "HIGH",
                    context.report == null ? "缺少匹配报告时，这只是可用版本建议，不是强适配结论。" : "推荐基于匹配报告和简历版本绑定关系。",
                    List.of("resume", "match")));
        }
        if (hasReadyProjectEvidence(context.projectEvidence)) {
            suggestions.add(suggestion("project-evidence", "PROJECT_EVIDENCE", "优先使用已选项目证据",
                    "这些项目证据完整度较高，可作为面试深挖和 JD 支撑材料。",
                    "MEDIUM", "MVP 仅按目标岗位绑定和完整度做轻量判断，P1 再做 JD 要求级聚合。",
                    projectEvidenceSourceIds(context)));
        } else {
            suggestions.add(suggestion("project-evidence-gap", "PROJECT_EVIDENCE", "先补项目证据再评估投递准备度",
                    "当前证据不足，系统只生成补证据行动，不输出直接投递结论。",
                    "LOW", "低证据建议必须降级为补资料或补证据行动。",
                    projectEvidenceSourceIds(context)));
        }
        suggestions.add(suggestion("application-boundary", "APPLICATION_RECORD", "创建的是投递记录，不是自动投递",
                "投递包只帮助准备、记录和跟进，真实投递仍需用户自行完成。",
                "HIGH", "系统不会自动发送邮件、消息或招聘平台投递。",
                List.of("jd")));
        return suggestions;
    }

    private JobApplicationPackageVO.SuggestionTraceVO buildTrace(PreviewContext context, JobApplicationPackageVO vo) {
        JobApplicationPackageVO.SuggestionTraceVO trace = new JobApplicationPackageVO.SuggestionTraceVO();
        trace.setTraceId("application-package-preview:" + vo.getUserId() + ":" + nullSafe(vo.getTargetJobId())
                + ":" + nullSafe(vo.getRecommendedResumeVersionId()) + ":" + nullSafe(vo.getMatchReportId()));
        trace.setFallback(false);
        trace.setDegraded(true);
        trace.setMock(false);
        trace.setInputSummary("targetJobId=" + nullSafe(vo.getTargetJobId())
                + ", jdAnalysisId=" + nullSafe(vo.getJdAnalysisId())
                + ", resumeVersionId=" + nullSafe(vo.getRecommendedResumeVersionId())
                + ", matchReportId=" + nullSafe(vo.getMatchReportId())
                + ", projectEvidenceCount=" + context.projectEvidence.size());
        trace.setOutputSummary("MVP 使用现有数据做规则聚合，未调用 AI、未写入投递包持久化表。");
        return trace;
    }

    private void applyReadiness(JobApplicationPackageVO vo, PreviewContext context) {
        if (context.analysis == null) {
            vo.setReadinessLevel("BLOCKED");
            vo.setReadinessReason("缺少已解析 JD，无法生成可信投递包。");
            return;
        }
        if (context.resumeVersion == null) {
            vo.setReadinessLevel("NEEDS_RESUME");
            vo.setReadinessReason("缺少可用简历版本。");
            return;
        }
        if (!matchSuccess(context.report) || scoreBelow(context.report.getOverallScore(), MATCH_MIN_SCORE)) {
            vo.setReadinessLevel("NEEDS_RESUME");
            vo.setReadinessReason("缺少成功匹配报告或匹配分过低，建议先优化简历/重新匹配。");
            return;
        }
        if (!hasReadyProjectEvidence(context.projectEvidence)) {
            vo.setReadinessLevel("NEEDS_EVIDENCE");
            vo.setReadinessReason("项目证据不足，建议先补充可用于面试深挖的项目材料。");
            return;
        }
        if (scoreBelow(context.report.getOverallScore(), MATCH_READY_SCORE)) {
            vo.setReadinessLevel("NEEDS_TRAINING");
            vo.setReadinessReason("基础材料可用，但匹配分未达到 READY 阈值，建议先做一次岗位上下文模拟面试。");
            return;
        }
        vo.setReadinessLevel("READY");
        vo.setReadinessReason("JD、简历版本、匹配报告和项目证据均达到 MVP 投递前检查要求。");
    }

    private String applicationNote(ApplicationPackageCreateApplicationDTO request, JobApplicationPackageVO preview) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(request.getNote())) {
            parts.add(request.getNote());
        }
        parts.add("来自投递包预览：" + preview.getId());
        parts.add("readiness=" + preview.getReadinessLevel() + "，" + preview.getReadinessReason());
        if (!CollectionUtils.isEmpty(preview.getProjectEvidenceIds())) {
            parts.add("projectEvidenceIds=" + preview.getProjectEvidenceIds());
        }
        parts.add("边界：仅创建个人投递记录，不会自动投递或发送外部消息。");
        return String.join("\n", parts);
    }

    private JobApplicationPackageVO.ProjectEvidenceSummaryVO toProjectEvidenceSummary(ProjectEvidence evidence) {
        JobApplicationPackageVO.ProjectEvidenceSummaryVO vo = new JobApplicationPackageVO.ProjectEvidenceSummaryVO();
        vo.setId(evidence.getId());
        vo.setTitle(evidence.getTitle());
        vo.setRole(evidence.getRole());
        vo.setTechStack(evidence.getTechStack());
        vo.setCompletenessScore(evidence.getCompletenessScore());
        vo.setCompletenessStatus(evidence.getCompletenessStatus());
        vo.setMissingFields(splitCsv(evidence.getMissingFields()));
        return vo;
    }

    private JobApplicationPackageVO.EvidenceSourceVO evidenceSource(String id, String type, String sourceId,
                                                                    String title, String summary, String confidence) {
        JobApplicationPackageVO.EvidenceSourceVO vo = new JobApplicationPackageVO.EvidenceSourceVO();
        vo.setId(id);
        vo.setSourceType(type);
        vo.setSourceId(sourceId);
        vo.setTitle(title);
        vo.setSummary(summary);
        vo.setConfidence(confidence);
        return vo;
    }

    private JobApplicationPackageVO.ApplicationPackageChecklistItemVO check(String key, String label, boolean passed,
                                                                            String reason, String severity,
                                                                            String actionType, String actionUrl,
                                                                            List<String> evidenceSourceIds) {
        JobApplicationPackageVO.ApplicationPackageChecklistItemVO vo = new JobApplicationPackageVO.ApplicationPackageChecklistItemVO();
        vo.setKey(key);
        vo.setLabel(label);
        vo.setPassed(passed);
        vo.setReason(reason);
        vo.setSeverity(severity);
        vo.setActionType(actionType);
        vo.setActionUrl(actionUrl);
        vo.setEvidenceSourceIds(evidenceSourceIds == null ? List.of() : evidenceSourceIds);
        return vo;
    }

    private JobApplicationPackageVO.CareerRiskSignalVO risk(String key, String level, String title,
                                                            String description, List<String> evidenceSourceIds) {
        JobApplicationPackageVO.CareerRiskSignalVO vo = new JobApplicationPackageVO.CareerRiskSignalVO();
        vo.setKey(key);
        vo.setLevel(level);
        vo.setTitle(title);
        vo.setDescription(description);
        vo.setEvidenceSourceIds(evidenceSourceIds == null ? List.of() : evidenceSourceIds);
        return vo;
    }

    private JobApplicationPackageVO.CareerActionItemVO action(String id, String actionType, String title,
                                                              String description, String priority, String actionUrl,
                                                              String sourceType, String sourceId,
                                                              List<String> evidenceSourceIds) {
        JobApplicationPackageVO.CareerActionItemVO vo = new JobApplicationPackageVO.CareerActionItemVO();
        vo.setId(id);
        vo.setActionType(actionType);
        vo.setTitle(title);
        vo.setDescription(description);
        vo.setPriority(priority);
        vo.setStatus("PENDING");
        vo.setActionUrl(actionUrl);
        vo.setSourceType(sourceType);
        vo.setSourceId(sourceId);
        vo.setEvidenceSourceIds(evidenceSourceIds == null ? List.of() : evidenceSourceIds);
        return vo;
    }

    private JobApplicationPackageVO.ExplainableSuggestionVO suggestion(String id, String type, String title,
                                                                       String content, String confidence,
                                                                       String boundary,
                                                                       List<String> evidenceSourceIds) {
        JobApplicationPackageVO.ExplainableSuggestionVO vo = new JobApplicationPackageVO.ExplainableSuggestionVO();
        vo.setId(id);
        vo.setSuggestionType(type);
        vo.setTitle(title);
        vo.setContent(content);
        vo.setConfidence(confidence);
        vo.setBoundary(boundary);
        vo.setEvidenceSourceIds(evidenceSourceIds == null ? List.of() : evidenceSourceIds);
        return vo;
    }

    private String previewId(PreviewContext context) {
        return "preview:"
                + nullSafe(context.targetJob == null ? context.targetJobId : context.targetJob.getId())
                + ":"
                + nullSafe(context.analysis == null ? context.jdAnalysisId : context.analysis.getId())
                + ":"
                + nullSafe(context.resumeVersion == null ? context.resumeVersionId : context.resumeVersion.getId())
                + ":"
                + nullSafe(context.report == null ? context.matchReportId : context.report.getId());
    }

    private List<String> suggestedProjectFields(List<ProjectEvidence> evidence) {
        Set<String> fields = new LinkedHashSet<>();
        if (evidence == null || evidence.isEmpty()) {
            fields.addAll(List.of("responsibility", "difficulty", "solution", "result"));
            return new ArrayList<>(fields);
        }
        for (ProjectEvidence item : evidence) {
            fields.addAll(splitCsv(item.getMissingFields()));
        }
        if (fields.isEmpty()) {
            fields.add("interviewDeepDiveStory");
        }
        return new ArrayList<>(fields);
    }

    private boolean detailEvidenceReady(ResumeJobMatchDetail detail) {
        return detail != null
                && !scoreBelow(detail.getScore(), MATCH_MIN_SCORE)
                && StringUtils.hasText(detail.getEvidence())
                && !StringUtils.hasText(detail.getGapDescription());
    }

    private boolean hasReadyProjectEvidence(List<ProjectEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return false;
        }
        return evidence.stream().anyMatch(this::evidenceCompletenessReady);
    }

    private boolean evidenceCompletenessReady(ProjectEvidence evidence) {
        if (evidence == null) {
            return false;
        }
        if (evidence.getCompletenessScore() != null && evidence.getCompletenessScore() >= PROJECT_READY_SCORE) {
            return true;
        }
        return "COMPLETE".equalsIgnoreCase(evidence.getCompletenessStatus())
                || "READY".equalsIgnoreCase(evidence.getCompletenessStatus());
    }

    private boolean matchSuccess(ResumeJobMatchReport report) {
        return report != null && ResumeJobMatchStatus.SUCCESS.getCode().equals(report.getStatus());
    }

    private boolean scoreAtLeast(Integer score, int threshold) {
        return score != null && score >= threshold;
    }

    private boolean scoreBelow(Integer score, int threshold) {
        return score == null || score < threshold;
    }

    private Boolean findChecklist(JobApplicationPackageVO vo, String key) {
        if (vo == null || vo.getChecklist() == null) {
            return null;
        }
        return vo.getChecklist().stream()
                .filter(item -> Objects.equals(item.getKey(), key))
                .map(JobApplicationPackageVO.ApplicationPackageChecklistItemVO::getPassed)
                .findFirst()
                .orElse(null);
    }

    private List<String> projectEvidenceSourceIds(PreviewContext context) {
        if (context == null || context.projectEvidence == null || context.projectEvidence.isEmpty()) {
            return List.of();
        }
        return context.projectEvidence.stream()
                .map(ProjectEvidence::getId)
                .filter(Objects::nonNull)
                .map(id -> "project:" + id)
                .toList();
    }

    private List<String> jsonTexts(String json, int max) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            collectJsonTexts(root, result, max);
        } catch (Exception ignored) {
            return List.of();
        }
        return result;
    }

    private void collectJsonTexts(JsonNode node, List<String> result, int max) {
        if (node == null || result.size() >= max) {
            return;
        }
        if (node.isTextual()) {
            addUnique(result, node.asText());
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectJsonTexts(child, result, max);
                if (result.size() >= max) {
                    break;
                }
            }
            return;
        }
        if (node.isObject()) {
            for (String field : List.of("name", "skill", "requirement", "title", "description", "summary", "topic")) {
                JsonNode value = node.get(field);
                if (value != null && value.isValueNode() && StringUtils.hasText(value.asText())) {
                    addUnique(result, value.asText());
                    return;
                }
            }
            node.fields().forEachRemaining(entry -> {
                if (result.size() < max) {
                    collectJsonTexts(entry.getValue(), result, max);
                }
            });
        }
    }

    private List<String> splitCsv(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : text.split("[,，\\n]")) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private List<String> firstNonEmpty(List<String> first, List<String> second) {
        return first == null || first.isEmpty() ? (second == null ? List.of() : second) : first;
    }

    private void addUnique(List<String> list, String value) {
        if (list != null && StringUtils.hasText(value) && !list.contains(value)) {
            list.add(value);
        }
    }

    private Long firstLong(Long first, Long second) {
        return first == null ? second : first;
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

    private void normalizePersistentActions(JobApplicationPackageVO vo, Long packageId) {
        if (vo == null || vo.getActions() == null) {
            return;
        }
        for (JobApplicationPackageVO.CareerActionItemVO action : vo.getActions()) {
            action.setSourceType("APPLICATION_PACKAGE");
            action.setSourceId(String.valueOf(packageId));
            if ("PRACTICE_INTERVIEW".equalsIgnoreCase(action.getActionType())) {
                action.setActionUrl("/interviews/create");
            } else if ("CREATE_APPLICATION_RECORD".equalsIgnoreCase(action.getActionType())) {
                action.setActionUrl("/applications");
            }
        }
    }

    private String newPackageNo(Long userId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return "APP-PKG-" + userId + "-" + suffix;
    }

    private String defaultPackageStatus(JobApplicationPackageVO snapshot) {
        return "READY".equalsIgnoreCase(snapshot == null ? null : snapshot.getReadinessLevel()) ? "READY" : "DRAFT";
    }

    private String normalizePackageStatusFilter(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("AVAILABLE".equals(normalized)) {
            return "READY";
        }
        return PACKAGE_STATUSES.contains(normalized) ? normalized : INVALID_PACKAGE_STATUS_FILTER;
    }

    private String normalizePackageStatusValue(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("AVAILABLE".equals(normalized)) {
            return "READY";
        }
        return PACKAGE_STATUSES.contains(normalized) ? normalized : null;
    }

    private Integer readinessScore(JobApplicationPackageVO snapshot) {
        if (snapshot == null) {
            return 0;
        }
        Integer matchScore = snapshot.getMatchSummary() == null ? null : snapshot.getMatchSummary().getOverallScore();
        int score = matchScore == null ? 0 : Math.max(0, Math.min(100, matchScore));
        if ("READY".equalsIgnoreCase(snapshot.getReadinessLevel())) {
            return Math.max(score, MATCH_READY_SCORE);
        }
        if ("BLOCKED".equalsIgnoreCase(snapshot.getReadinessLevel())) {
            return Math.min(score, 30);
        }
        return score;
    }

    private String packageApplicationNote(PackageRow row, JobApplicationPackageVO detail,
                                          ApplicationPackageActionExecuteDTO dto) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(dto == null ? null : dto.getNote())) {
            parts.add(dto.getNote());
        }
        parts.add("Created from application package: " + row.id);
        parts.add("packageNo=" + row.packageNo);
        parts.add("readiness=" + detail.getReadinessLevel() + ", " + detail.getReadinessReason());
        parts.add("Boundary: record only; no external delivery or message was sent.");
        return String.join("\n", parts);
    }

    private String stringPayload(ApplicationPackageActionExecuteDTO dto, String key) {
        if (dto == null || dto.getPayload() == null || key == null) {
            return null;
        }
        Object value = dto.getPayload().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private JobApplicationPackageVO readPackageSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, JobApplicationPackageVO.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> List<T> readList(String json, Class<T> elementType) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Long> readLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to serialize application package snapshot");
        }
    }

    private Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    private LocalDateTime getLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean getBoolean(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value == 1;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> payloadOf(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (pairs == null) {
            return payload;
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object key = pairs[i];
            if (key != null) {
                payload.put(String.valueOf(key), pairs[i + 1]);
            }
        }
        return payload;
    }

    private String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String targetJobUrl(Long targetJobId) {
        return targetJobId == null ? "/job-targets" : "/job-targets/" + targetJobId;
    }

    private String matchUrl(Long matchReportId) {
        return matchReportId == null ? "/resume-match" : "/resume-match/" + matchReportId;
    }

    private String projectEvidenceUrl(Long targetJobId) {
        return targetJobId == null ? "/project-evidence" : "/project-evidence?targetJobId=" + targetJobId;
    }

    private static class PreviewContext {
        private Long targetJobId;
        private Long jdAnalysisId;
        private Long resumeVersionId;
        private Long matchReportId;
        private TargetJob targetJob;
        private JobDescriptionAnalysis analysis;
        private ResumeVersion resumeVersion;
        private ResumeJobMatchReport report;
        private List<ResumeJobMatchDetail> matchDetails = List.of();
        private List<ProjectEvidence> projectEvidence = List.of();
    }

    private static class PackageRow {
        private Long id;
        private Long userId;
        private String packageNo;
        private Long targetJobId;
        private Long jdAnalysisId;
        private Long resumeVersionId;
        private Long matchReportId;
        private Long applicationId;
        private String companyName;
        private String jobTitle;
        private String readinessLevel;
        private Integer readinessScore;
        private String readinessReason;
        private String packageStatus;
        private String snapshotJson;
        private String checklistJson;
        private String actionsJson;
        private String projectEvidenceIdsJson;
        private String traceId;
        private String resultSource;
        private Boolean fallback;
        private String fallbackReason;
        private Integer snapshotVersion;
        private LocalDateTime refreshedAt;
    }
}
