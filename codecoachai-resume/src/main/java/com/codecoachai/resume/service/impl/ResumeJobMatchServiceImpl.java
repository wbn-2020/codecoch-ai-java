package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.BaseEntity;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeJobMatchCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeJobMatchQueryDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeJobMatchDetail;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.vo.ResumeJobMatchDetailItemVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportDetailVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportListVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.dto.AnalyzeResumeJobMatchDTO;
import com.codecoachai.resume.feign.vo.AnalyzeResumeJobMatchVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchDetailMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.ResumeJobMatchMqDispatcher;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeJobMatchServiceImpl implements ResumeJobMatchService {

    private static final long DEFAULT_PAGE_NO = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String MATCH_SOURCE_TYPE = "RESUME_JOB_MATCH";
    private static final String MATCH_ASYNC_BIZ_TYPE = "resume-job-match.analyze";
    private static final String TRUST_VERIFIED = "VERIFIED";
    private static final String TRUST_PARTIAL = "PARTIAL";
    private static final String TRUST_FALLBACK = "FALLBACK";

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final ResumeJobMatchReportMapper reportMapper;
    private final ResumeJobMatchDetailMapper detailMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Optional<ResumeJobMatchMqDispatcher> resumeJobMatchMqDispatcher;

    @Override
    public ResumeJobMatchSubmitVO createReport(ResumeJobMatchCreateDTO dto) {
        Long userId = requireCurrentUserId();
        MatchContext context = prepareContext(dto.getResumeId(), dto.getTargetJobId(), userId,
                dto.getResumeVersionId(), null);
        if (!Boolean.TRUE.equals(dto.getForceRefresh())) {
            ResumeJobMatchReport existing = latestSuccessfulReport(dto.getResumeId(), dto.getTargetJobId(), userId,
                    dto.getResumeVersionId());
            if (existing != null && !isReportStale(existing, context)) {
                return toSubmitVO(existing);
            }
        }
        ResumeJobMatchReport report = transactionTemplate.execute(status -> createProcessingReport(context));
        MqDispatchReceipt receipt = dispatchAnalyze(report);
        if (receipt != null) {
            return withAsyncReceipt(toSubmitVO(report), receipt);
        }
        return generateReport(report.getId());
    }

    @Override
    public PageResult<ResumeJobMatchReportListVO> listReports(ResumeJobMatchQueryDTO query) {
        Long userId = requireCurrentUserId();
        ResumeJobMatchQueryDTO request = query == null ? new ResumeJobMatchQueryDTO() : query;
        long pageNo = normalizePageNo(request.getPageNo());
        long pageSize = normalizePageSize(request.getPageSize());

        LambdaQueryWrapper<ResumeJobMatchReport> wrapper = new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeJobMatchReport::getCreatedAt);
        if (request.getResumeId() != null) {
            wrapper.eq(ResumeJobMatchReport::getResumeId, request.getResumeId());
        }
        if (request.getResumeVersionId() != null) {
            wrapper.eq(ResumeJobMatchReport::getResumeVersionId, request.getResumeVersionId());
        }
        if (request.getTargetJobId() != null) {
            wrapper.eq(ResumeJobMatchReport::getTargetJobId, request.getTargetJobId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(ResumeJobMatchReport::getStatus, request.getStatus());
        }

        Page<ResumeJobMatchReport> page = reportMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ResumeJobMatchReportListVO> records = page.getRecords().stream()
                .map(this::toListVO)
                .toList();
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public ResumeJobMatchReportDetailVO getReport(Long id) {
        Long userId = requireCurrentUserId();
        return toDetailVO(getOwnedReport(id, userId));
    }

    @Override
    public ResumeJobMatchReportDetailVO getLatest(Long resumeId, Long targetJobId, Long resumeVersionId) {
        Long userId = requireCurrentUserId();
        LambdaQueryWrapper<ResumeJobMatchReport> wrapper = new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeJobMatchReport::getCreatedAt)
                .last("limit 1");
        if (resumeId != null) {
            getOwnedResume(resumeId, userId);
            wrapper.eq(ResumeJobMatchReport::getResumeId, resumeId);
        }
        if (resumeVersionId != null) {
            getOwnedResumeVersion(resumeVersionId, resumeId, userId);
            wrapper.eq(ResumeJobMatchReport::getResumeVersionId, resumeVersionId);
        } else if (resumeId != null) {
            wrapper.isNull(ResumeJobMatchReport::getResumeVersionId);
        }
        if (targetJobId != null) {
            getOwnedTargetJob(targetJobId, userId);
            wrapper.eq(ResumeJobMatchReport::getTargetJobId, targetJobId);
        }
        ResumeJobMatchReport report = reportMapper.selectOne(wrapper);
        return report == null ? null : toDetailVO(report);
    }

    @Override
    public ResumeJobMatchReportDetailVO getInnerSuccessReport(Long id) {
        Long userId = requireCurrentUserId();
        ResumeJobMatchReport report = getOwnedReport(id, userId);
        if (!isTrustedSuccessReport(report)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前匹配报告来源或内容仍需复核，暂不适合继续生成训练或面试建议，请重新生成后再试");
        }
        return toDetailVO(report);
    }

    @Override
    public ResumeJobMatchReportAgentEvidenceVO getReportEvidence(Long userId, Long reportId) {
        if (userId == null || reportId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId and reportId are required");
        }
        ResumeJobMatchReport report = reportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getId, reportId)
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getStatus, ResumeJobMatchStatus.SUCCESS.getCode())
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume job match report evidence not found");
        }
        ResumeJobMatchReportAgentEvidenceVO vo = new ResumeJobMatchReportAgentEvidenceVO();
        vo.setId(report.getId());
        vo.setUserId(report.getUserId());
        vo.setResumeId(report.getResumeId());
        vo.setTargetJobId(report.getTargetJobId());
        vo.setResumeVersionId(report.getResumeVersionId());
        vo.setJdAnalysisId(report.getJdAnalysisId());
        vo.setStatus(report.getStatus());
        vo.setGeneratedAt(report.getUpdatedAt() == null ? report.getCreatedAt() : report.getUpdatedAt());
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }

    @Override
    public ResumeJobMatchSubmitVO regenerate(Long id) {
        Long userId = requireCurrentUserId();
        ResumeJobMatchReport oldReport = getOwnedReport(id, userId);
        MatchContext context = prepareContext(oldReport.getResumeId(), oldReport.getTargetJobId(), userId,
                oldReport.getResumeVersionId(), oldReport.getJdAnalysisId());
        ResumeJobMatchReport newReport = transactionTemplate.execute(status -> createProcessingReport(context));
        MqDispatchReceipt receipt = dispatchAnalyze(newReport);
        if (receipt != null) {
            return withAsyncReceipt(toSubmitVO(newReport), receipt);
        }
        return generateReport(newReport.getId());
    }

    @Override
    public ResumeJobMatchSubmitVO executeReport(Long id) {
        return generateReport(id);
    }

    private ResumeJobMatchSubmitVO generateReport(Long reportId) {
        ResumeJobMatchReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume job match report missing");
        }
        try {
            MatchContext context = prepareContext(report.getResumeId(), report.getTargetJobId(), report.getUserId(),
                    report.getResumeVersionId(), report.getJdAnalysisId());
            AnalyzeResumeJobMatchVO response = FeignResultUtils.unwrap(aiFeignClient.analyzeResumeJobMatch(toAiRequest(report, context)));
            JsonNode resultJson = parseResultJson(response == null ? null : response.getResultJson());
            ResumeJobMatchReport success = transactionTemplate.execute(status ->
                    markSuccess(report.getId(), resultJson, response == null ? null : response.getAiCallLogId()));
            return toSubmitVO(success);
        } catch (RuntimeException ex) {
            log.warn("Resume job match generation failed, reportId={}", report.getId(), ex);
            ResumeJobMatchReport failed = transactionTemplate.execute(status -> markFailed(report.getId(), ex));
            return toSubmitVO(failed);
        }
    }

    private MqDispatchReceipt dispatchAnalyze(ResumeJobMatchReport report) {
        return report == null ? null : resumeJobMatchMqDispatcher
                .map(dispatcher -> dispatcher.dispatchAnalyzeWithReceipt(report.getId(), report.getUserId()))
                .orElse(null);
    }

    private MatchContext prepareContext(Long resumeId, Long targetJobId, Long userId,
                                        Long resumeVersionId, Long jdAnalysisId) {
        Resume resume = getOwnedResume(resumeId, userId);
        ResumeVersion resumeVersion = resumeVersionId == null
                ? null
                : getOwnedResumeVersion(resumeVersionId, resumeId, userId);
        List<ResumeProject> projects = projectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, resumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeProject::getSortOrder)
                .orderByAsc(ResumeProject::getSort)
                .orderByDesc(ResumeProject::getUpdatedAt));
        ResumeAnalysisRecord resumeAnalysis = latestSuccessfulResumeAnalysis(resumeId, userId);
        TargetJob targetJob = getOwnedTargetJob(targetJobId, userId);
        JobDescriptionAnalysis jdAnalysis = jdAnalysisId == null
                ? latestParsedJdAnalysis(targetJobId, userId)
                : getOwnedJdAnalysis(jdAnalysisId, targetJobId, userId);
        return new MatchContext(resume, resumeVersion, projects, resumeAnalysis, targetJob, jdAnalysis);
    }

    private ResumeJobMatchReport createProcessingReport(MatchContext context) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setUserId(context.resume().getUserId());
        report.setResumeId(context.resume().getId());
        report.setResumeVersionId(context.resumeVersion() == null ? null : context.resumeVersion().getId());
        report.setTargetJobId(context.targetJob().getId());
        report.setJdAnalysisId(context.jdAnalysis().getId());
        report.setStatus(ResumeJobMatchStatus.PROCESSING.getCode());
        reportMapper.insert(report);
        return report;
    }

    private AnalyzeResumeJobMatchDTO toAiRequest(ResumeJobMatchReport report, MatchContext context) {
        AnalyzeResumeJobMatchDTO request = new AnalyzeResumeJobMatchDTO();
        request.setReportId(report.getId());
        request.setUserId(report.getUserId());
        request.setResumeId(report.getResumeId());
        request.setResumeVersionId(report.getResumeVersionId());
        request.setTargetJobId(report.getTargetJobId());
        request.setJdAnalysisId(report.getJdAnalysisId());
        request.setResumeAnalysisJson(context.resumeVersion() == null && context.resumeAnalysis() != null
                ? context.resumeAnalysis().getStructuredJson()
                : null);
        request.setResumeSnapshotJson(resumeSnapshotJson(context));
        request.setJobDescriptionAnalysisJson(toJson(jobDescriptionSnapshot(context.jdAnalysis())));
        request.setTargetJobJson(toJson(targetJobSnapshot(context.targetJob())));
        request.setUserExperienceYears(userExperienceYears(context));
        return request;
    }

    private ResumeJobMatchReport markSuccess(Long reportId, JsonNode resultJson, Long aiCallLogId) {
        ResumeJobMatchReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume job match report missing");
        }
        ObjectNode normalizedResult = normalizeStoredMatchResult(resultJson);
        JsonNode dimensionScores = normalizedResult.path("dimensionScores");
        if (!isFallbackMatchResult(normalizedResult)) {
            validateSuccessResult(normalizedResult, dimensionScores);
        }
        List<ResumeJobMatchDetail> details = buildDetails(report, normalizedResult);
        if (details.isEmpty()) {
            markStoredSchemaWarning(ensureSchemaWarningsArray(normalizedResult),
                    "details", "missing detail evidence fallback detail generated");
            normalizedResult.put("trustStatus", TRUST_PARTIAL);
            details.add(buildFallbackDetail(report, normalizedResult));
        }
        report.setOverallScore(requireScore(normalizedResult, "overallScore"));
        report.setTechStackScore(requireScore(dimensionScores, "techStack"));
        report.setProjectExperienceScore(requireScore(dimensionScores, "projectExperience"));
        report.setBusinessFitScore(requireScore(dimensionScores, "businessFit"));
        report.setCommunicationScore(requireScore(dimensionScores, "communication"));
        report.setStrengthsJson(jsonArrayText(normalizedResult, "strengths"));
        report.setGapsJson(jsonArrayText(normalizedResult, "gaps"));
        report.setResumeRisksJson(jsonArrayText(normalizedResult, "resumeRisks"));
        report.setOptimizationSuggestionsJson(jsonArrayText(normalizedResult, "optimizationSuggestions"));
        report.setRecommendedLearningTopicsJson(jsonArrayText(normalizedResult, "recommendedLearningTopics"));
        report.setRecommendedInterviewTopicsJson(jsonArrayText(normalizedResult, "recommendedInterviewTopics"));
        report.setSummary(textValue(normalizedResult, "summary"));
        report.setRawResultJson(normalizedResult.toString());
        report.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        report.setErrorMessage(null);
        report.setAiCallLogId(aiCallLogId);
        reportMapper.updateById(report);
        replaceDetails(report, details);
        return reportMapper.selectById(reportId);
    }

    private ResumeJobMatchReport markFailed(Long reportId, RuntimeException ex) {
        ResumeJobMatchReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume job match report missing");
        }
        report.setStatus(ResumeJobMatchStatus.FAILED.getCode());
        report.setErrorMessage(safeUserFacingMatchError(ex));
        report.setRawResultJson(failedMatchDiagnosticJson(ex));
        reportMapper.updateById(report);
        detailMapper.delete(new LambdaQueryWrapper<ResumeJobMatchDetail>()
                .eq(ResumeJobMatchDetail::getReportId, reportId)
                .eq(ResumeJobMatchDetail::getUserId, report.getUserId()));
        return reportMapper.selectById(reportId);
    }

    private void replaceDetails(ResumeJobMatchReport report, List<ResumeJobMatchDetail> details) {
        detailMapper.delete(new LambdaQueryWrapper<ResumeJobMatchDetail>()
                .eq(ResumeJobMatchDetail::getReportId, report.getId())
                .eq(ResumeJobMatchDetail::getUserId, report.getUserId()));
        for (ResumeJobMatchDetail detail : details) {
            detailMapper.insert(detail);
        }
    }

    private List<ResumeJobMatchDetail> buildDetails(ResumeJobMatchReport report, JsonNode resultJson) {
        List<ResumeJobMatchDetail> details = new ArrayList<>();
        JsonNode gaps = resultJson.path("gaps");
        if (gaps.isArray()) {
            for (JsonNode gap : gaps) {
                if (!gap.isObject()) {
                    continue;
                }
                ResumeJobMatchDetail detail = baseDetail(report);
                detail.setDimension(firstText(textValue(gap, "category"), "GAP"));
                detail.setSkillName(firstText(textValue(gap, "skillName"), "待确认技能"));
                detail.setMatchLevel(firstText(textValue(gap, "severity"), "MEDIUM"));
                detail.setScore(scoreFromLevels(gap));
                detail.setEvidence(firstText(textValue(gap, "evidence"), "AI 返回该差距但缺少结构化来源说明，已标记为待人工复核。"));
                detail.setGapDescription(firstText(textValue(gap, "description"), "AI 返回该差距但缺少结构化描述，已标记为待人工复核。"));
                detail.setSuggestion(jsonOrText(gap.path("recommendedActions")));
                details.add(detail);
            }
        }
        JsonNode strengths = resultJson.path("strengths");
        if (strengths.isArray()) {
            for (JsonNode strength : strengths) {
                if (!strength.isObject()) {
                    continue;
                }
                ResumeJobMatchDetail detail = baseDetail(report);
                detail.setDimension("STRENGTH");
                detail.setSkillName(jsonOrText(strength.path("relatedSkills")));
                detail.setMatchLevel("MATCHED");
                detail.setScore(null);
                detail.setEvidence(firstText(textValue(strength, "evidence"), "AI 返回该优势但缺少结构化来源说明，已标记为待人工复核。"));
                detail.setGapDescription(null);
                detail.setSuggestion(firstText(textValue(strength, "title"), "待复核匹配优势"));
                details.add(detail);
            }
        }
        return details;
    }

    private ResumeJobMatchDetail buildFallbackDetail(ResumeJobMatchReport report, JsonNode resultJson) {
        ResumeJobMatchDetail detail = baseDetail(report);
        detail.setDimension("REVIEW_REQUIRED");
        detail.setSkillName("待复核匹配结论");
        detail.setMatchLevel("PARTIAL");
        detail.setScore(integerValue(resultJson, "overallScore"));
        detail.setEvidence("AI 返回了部分匹配结果，但缺少可直接引用的来源说明，系统已保守标记为待人工复核。");
        detail.setGapDescription(firstText(textValue(resultJson, "summary"),
                "匹配报告已生成待复核结果，请先查看报告摘要、岗位描述和简历输入后再用于训练。"));
        detail.setSuggestion("重新生成匹配报告，或补充简历项目经历和岗位关键要求后再生成。");
        return detail;
    }

    private ResumeJobMatchDetail baseDetail(ResumeJobMatchReport report) {
        ResumeJobMatchDetail detail = new ResumeJobMatchDetail();
        detail.setReportId(report.getId());
        detail.setUserId(report.getUserId());
        return detail;
    }

    private Resume getOwnedResume(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择简历");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, id)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历不存在或已不可用");
        }
        return resume;
    }

    private ResumeVersion getOwnedResumeVersion(Long versionId, Long resumeId, Long userId) {
        if (versionId == null) {
            return null;
        }
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历版本不存在或已不可用");
        }
        if (resumeId != null && !Objects.equals(resumeId, version.getResumeId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历版本不属于当前简历");
        }
        return version;
    }

    private TargetJob getOwnedTargetJob(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择目标岗位");
        }
        TargetJob job = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, id)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (job == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不存在或已不可用");
        }
        return job;
    }

    private ResumeJobMatchReport getOwnedReport(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择匹配报告");
        }
        ResumeJobMatchReport report = reportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getId, id)
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历匹配报告不存在或已不可用");
        }
        return report;
    }

    private ResumeAnalysisRecord latestSuccessfulResumeAnalysis(Long resumeId, Long userId) {
        return analysisRecordMapper.selectOne(new LambdaQueryWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getResumeId, resumeId)
                .eq(ResumeAnalysisRecord::getUserId, userId)
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.SUCCESS.getCode())
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeAnalysisRecord::getUpdatedAt)
                .last("limit 1"));
    }

    private JobDescriptionAnalysis latestParsedJdAnalysis(Long targetJobId, Long userId) {
        JobDescriptionAnalysis analysis = jobDescriptionAnalysisMapper.selectOne(
                new LambdaQueryWrapper<JobDescriptionAnalysis>()
                        .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                        .eq(JobDescriptionAnalysis::getUserId, userId)
                        .eq(JobDescriptionAnalysis::getParseStatus, JobDescriptionParseStatus.PARSED.getCode())
                        .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                        .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                        .last("limit 1"));
        if (analysis == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位描述尚未完成分析");
        }
        return analysis;
    }

    private JobDescriptionAnalysis getOwnedJdAnalysis(Long jdAnalysisId, Long targetJobId, Long userId) {
        JobDescriptionAnalysis analysis = jobDescriptionAnalysisMapper.selectOne(
                new LambdaQueryWrapper<JobDescriptionAnalysis>()
                        .eq(JobDescriptionAnalysis::getId, jdAnalysisId)
                        .eq(JobDescriptionAnalysis::getUserId, userId)
                        .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                        .eq(JobDescriptionAnalysis::getParseStatus, JobDescriptionParseStatus.PARSED.getCode())
                        .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (analysis == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告绑定的岗位分析记录已不可用，请重新生成报告");
        }
        return analysis;
    }

    private ResumeJobMatchReport latestSuccessfulReport(Long resumeId, Long targetJobId, Long userId,
                                                        Long resumeVersionId) {
        ResumeJobMatchReport report = reportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getResumeId, resumeId)
                .eq(resumeVersionId != null, ResumeJobMatchReport::getResumeVersionId, resumeVersionId)
                .isNull(resumeVersionId == null, ResumeJobMatchReport::getResumeVersionId)
                .eq(ResumeJobMatchReport::getTargetJobId, targetJobId)
                .eq(ResumeJobMatchReport::getStatus, ResumeJobMatchStatus.SUCCESS.getCode())
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeJobMatchReport::getUpdatedAt)
                .last("limit 1"));
        return isTrustedSuccessReport(report) ? report : null;
    }

    private boolean isReportStale(ResumeJobMatchReport report, MatchContext context) {
        Long contextVersionId = context.resumeVersion() == null ? null : context.resumeVersion().getId();
        if (!Objects.equals(report.getResumeVersionId(), contextVersionId)) {
            return true;
        }
        LocalDateTime reportUpdatedAt = effectiveUpdatedAt(report);
        if (reportUpdatedAt == null) {
            return true;
        }
        if (context.resumeVersion() == null) {
            if (isNewerThanReport(context.resume(), reportUpdatedAt)) {
                return true;
            }
            if (isNewerThanReport(context.resumeAnalysis(), reportUpdatedAt)) {
                return true;
            }
        } else if (isNewerThanReport(context.resumeVersion(), reportUpdatedAt)) {
            return true;
        }
        if (isNewerThanReport(context.targetJob(), reportUpdatedAt)) {
            return true;
        }
        if (isNewerThanReport(context.jdAnalysis(), reportUpdatedAt)) {
            return true;
        }
        if (context.resumeVersion() != null && resumeVersionSnapshotHasProjects(context.resumeVersion())) {
            return false;
        }
        return context.projects().stream().anyMatch(project -> isNewerThanReport(project, reportUpdatedAt));
    }

    private boolean isNewerThanReport(BaseEntity entity, LocalDateTime reportUpdatedAt) {
        LocalDateTime updatedAt = effectiveUpdatedAt(entity);
        return updatedAt != null && updatedAt.isAfter(reportUpdatedAt);
    }

    private LocalDateTime effectiveUpdatedAt(BaseEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt();
    }

    private ResumeJobMatchReportListVO toListVO(ResumeJobMatchReport report) {
        Resume resume = resumeMapper.selectById(report.getResumeId());
        TargetJob job = targetJobMapper.selectById(report.getTargetJobId());
        ResumeVersion version = findReportVersion(report);
        ResumeJobMatchReportListVO vo = new ResumeJobMatchReportListVO();
        vo.setReportId(report.getId());
        vo.setResumeId(report.getResumeId());
        vo.setResumeTitle(resume == null ? null : resume.getTitle());
        vo.setResumeVersionId(report.getResumeVersionId());
        vo.setResumeVersionNo(version == null ? null : version.getVersionNo());
        vo.setResumeVersionName(version == null ? null : version.getVersionName());
        vo.setTargetJobId(report.getTargetJobId());
        vo.setJobTitle(job == null ? null : job.getJobTitle());
        vo.setCompanyName(job == null ? null : job.getCompanyName());
        vo.setOverallScore(report.getOverallScore());
        vo.setTechStackScore(report.getTechStackScore());
        vo.setProjectExperienceScore(report.getProjectExperienceScore());
        vo.setBusinessFitScore(report.getBusinessFitScore());
        vo.setCommunicationScore(report.getCommunicationScore());
        vo.setStatus(report.getStatus());
        vo.setSummary(report.getSummary());
        vo.setErrorMessage(report.getErrorMessage());
        vo.setSourceType(MATCH_SOURCE_TYPE);
        vo.setSourceId(report.getId());
        vo.setTrustStatus(matchTrustStatus(report));
        vo.setEvidenceSummary(matchEvidenceSummary(report));
        vo.setFallback(matchFallback(report));
        vo.setSchemaWarnings(matchSchemaWarnings(report));
        vo.setSchemaWarningCount(matchSchemaWarningCount(report));
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }

    private ResumeJobMatchReportDetailVO toDetailVO(ResumeJobMatchReport report) {
        Resume resume = resumeMapper.selectById(report.getResumeId());
        TargetJob job = targetJobMapper.selectById(report.getTargetJobId());
        ResumeVersion version = findReportVersion(report);
        ResumeJobMatchReportDetailVO vo = new ResumeJobMatchReportDetailVO();
        vo.setReportId(report.getId());
        vo.setUserId(report.getUserId());
        vo.setResumeId(report.getResumeId());
        vo.setResumeTitle(resume == null ? null : resume.getTitle());
        vo.setResumeVersionId(report.getResumeVersionId());
        vo.setResumeVersionNo(version == null ? null : version.getVersionNo());
        vo.setResumeVersionName(version == null ? null : version.getVersionName());
        vo.setTargetJobId(report.getTargetJobId());
        vo.setJobTitle(job == null ? null : job.getJobTitle());
        vo.setCompanyName(job == null ? null : job.getCompanyName());
        vo.setJdAnalysisId(report.getJdAnalysisId());
        vo.setOverallScore(report.getOverallScore());
        vo.setTechStackScore(report.getTechStackScore());
        vo.setProjectExperienceScore(report.getProjectExperienceScore());
        vo.setBusinessFitScore(report.getBusinessFitScore());
        vo.setCommunicationScore(report.getCommunicationScore());
        ArrayNode storedFieldWarnings = objectMapper.createArrayNode();
        vo.setStrengths(readStoredReportArrayOrEmpty(report.getStrengthsJson(), "strengths", storedFieldWarnings));
        vo.setGaps(readStoredReportArrayOrEmpty(report.getGapsJson(), "gaps", storedFieldWarnings));
        vo.setResumeRisks(readStoredReportArrayOrEmpty(report.getResumeRisksJson(), "resumeRisks", storedFieldWarnings));
        vo.setOptimizationSuggestions(readStoredReportArrayOrEmpty(report.getOptimizationSuggestionsJson(),
                "optimizationSuggestions", storedFieldWarnings));
        vo.setRecommendedLearningTopics(readStoredReportArrayOrEmpty(report.getRecommendedLearningTopicsJson(),
                "recommendedLearningTopics", storedFieldWarnings));
        vo.setRecommendedInterviewTopics(readStoredReportArrayOrEmpty(report.getRecommendedInterviewTopicsJson(),
                "recommendedInterviewTopics", storedFieldWarnings));
        vo.setSummary(report.getSummary());
        vo.setStatus(report.getStatus());
        vo.setErrorMessage(report.getErrorMessage());
        vo.setAiCallLogId(report.getAiCallLogId());
        vo.setSourceType(MATCH_SOURCE_TYPE);
        vo.setSourceId(report.getId());
        vo.setTrustStatus(matchTrustStatus(report));
        vo.setEvidenceSummary(matchEvidenceSummary(report));
        vo.setFallback(matchFallback(report));
        JsonNode schemaWarnings = mergeSchemaWarnings(matchSchemaWarnings(report), storedFieldWarnings);
        vo.setSchemaWarnings(schemaWarnings);
        vo.setSchemaWarningCount(schemaWarnings.isArray() ? schemaWarnings.size() : 0);
        vo.setAsyncBizType(MATCH_ASYNC_BIZ_TYPE);
        vo.setAsyncBizId(String.valueOf(report.getId()));
        vo.setAsyncSendStatus("QUERY_BY_BIZ_ID");
        vo.setDetails(listDetailItems(report));
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }

    private List<ResumeJobMatchDetailItemVO> listDetailItems(ResumeJobMatchReport report) {
        return detailMapper.selectList(new LambdaQueryWrapper<ResumeJobMatchDetail>()
                        .eq(ResumeJobMatchDetail::getReportId, report.getId())
                        .eq(ResumeJobMatchDetail::getUserId, report.getUserId())
                        .eq(ResumeJobMatchDetail::getDeleted, CommonConstants.NO)
                        .orderByAsc(ResumeJobMatchDetail::getId))
                .stream()
                .map(this::toDetailItemVO)
                .toList();
    }

    private ResumeJobMatchDetailItemVO toDetailItemVO(ResumeJobMatchDetail detail) {
        ResumeJobMatchDetailItemVO vo = new ResumeJobMatchDetailItemVO();
        vo.setId(detail.getId());
        vo.setDimension(detail.getDimension());
        vo.setSkillName(detail.getSkillName());
        vo.setMatchLevel(detail.getMatchLevel());
        vo.setScore(detail.getScore());
        vo.setEvidence(detail.getEvidence());
        vo.setGapDescription(detail.getGapDescription());
        vo.setSuggestion(detail.getSuggestion());
        vo.setCreatedAt(detail.getCreatedAt());
        vo.setUpdatedAt(detail.getUpdatedAt());
        return vo;
    }

    private ResumeJobMatchSubmitVO toSubmitVO(ResumeJobMatchReport report) {
        ResumeVersion version = findReportVersion(report);
        ResumeJobMatchSubmitVO vo = new ResumeJobMatchSubmitVO();
        vo.setReportId(report.getId());
        vo.setResumeId(report.getResumeId());
        vo.setResumeVersionId(report.getResumeVersionId());
        vo.setResumeVersionNo(version == null ? null : version.getVersionNo());
        vo.setResumeVersionName(version == null ? null : version.getVersionName());
        vo.setTargetJobId(report.getTargetJobId());
        vo.setJdAnalysisId(report.getJdAnalysisId());
        vo.setAiCallLogId(report.getAiCallLogId());
        vo.setStatus(report.getStatus());
        vo.setErrorMessage(report.getErrorMessage());
        vo.setSourceType(MATCH_SOURCE_TYPE);
        vo.setSourceId(report.getId());
        vo.setTrustStatus(matchTrustStatus(report));
        vo.setEvidenceSummary(matchEvidenceSummary(report));
        vo.setFallback(matchFallback(report));
        vo.setSchemaWarnings(matchSchemaWarnings(report));
        vo.setSchemaWarningCount(matchSchemaWarningCount(report));
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }

    private ResumeVersion findReportVersion(ResumeJobMatchReport report) {
        if (report == null || report.getResumeVersionId() == null) {
            return null;
        }
        return resumeVersionMapper.selectById(report.getResumeVersionId());
    }

    private ResumeJobMatchSubmitVO withAsyncReceipt(ResumeJobMatchSubmitVO vo, MqDispatchReceipt receipt) {
        if (vo == null || receipt == null) {
            return vo;
        }
        vo.setAsyncMessageId(receipt.getMessageId());
        vo.setAsyncTraceId(receipt.getTraceId());
        vo.setAsyncBizType(receipt.getBizType());
        vo.setAsyncBizId(receipt.getBizId());
        vo.setAsyncSendStatus(receipt.getSendStatus());
        return vo;
    }

    private String matchTrustStatus(ResumeJobMatchReport report) {
        if (report == null) {
            return TRUST_PARTIAL;
        }
        if (ResumeJobMatchStatus.FAILED.getCode().equals(report.getStatus())) {
            return TRUST_FALLBACK;
        }
        if (!ResumeJobMatchStatus.SUCCESS.getCode().equals(report.getStatus())) {
            return TRUST_PARTIAL;
        }
        if (matchRawFallback(report)) {
            return TRUST_FALLBACK;
        }
        boolean hasScore = report.getOverallScore() != null && report.getOverallScore() > 0;
        boolean hasAiLog = report.getAiCallLogId() != null;
        boolean hasEvidence = StringUtils.hasText(report.getStrengthsJson())
                || StringUtils.hasText(report.getGapsJson())
                || StringUtils.hasText(report.getSummary());
        String rawTrustStatus = matchRawTrustStatus(report);
        if (TRUST_FALLBACK.equals(rawTrustStatus)) {
            return TRUST_FALLBACK;
        }
        if (TRUST_PARTIAL.equals(rawTrustStatus) || hasMatchSchemaWarnings(report)) {
            return TRUST_PARTIAL;
        }
        return hasScore && hasAiLog && hasEvidence ? TRUST_VERIFIED : TRUST_PARTIAL;
    }

    private Boolean matchFallback(ResumeJobMatchReport report) {
        return report == null
                || ResumeJobMatchStatus.FAILED.getCode().equals(report.getStatus())
                || matchRawFallback(report)
                || TRUST_FALLBACK.equals(matchRawTrustStatus(report))
                || report.getResumeId() == null
                || report.getTargetJobId() == null
                || hasMatchSchemaWarnings(report);
    }

    private boolean isTrustedSuccessReport(ResumeJobMatchReport report) {
        return report != null
                && ResumeJobMatchStatus.SUCCESS.getCode().equals(report.getStatus())
                && !matchFallback(report)
                && TRUST_VERIFIED.equals(matchTrustStatus(report));
    }

    private String matchEvidenceSummary(ResumeJobMatchReport report) {
        if (report == null) {
            return "匹配报告缺少上下文来源。";
        }
        String resumeContext = report.getResumeVersionId() == null
                ? (report.getResumeId() == null ? "简历来源待确认" : "来自已绑定简历")
                : "来自已绑定简历版本 #" + report.getResumeVersionId();
        String context = resumeContext
                + " 与 " + (report.getTargetJobId() == null ? "目标岗位待确认" : "已绑定目标岗位");
        String jdAnalysis = report.getJdAnalysisId() == null ? "未绑定岗位分析记录" : "岗位分析已绑定";
        if (ResumeJobMatchStatus.FAILED.getCode().equals(report.getStatus())) {
            return context + " · " + jdAnalysis + " · 生成失败：" + firstText(report.getErrorMessage(), "原因待排查");
        }
        if (ResumeJobMatchStatus.SUCCESS.getCode().equals(report.getStatus())) {
            String aiLog = report.getAiCallLogId() == null ? "处理记录待补充" : "处理记录已保存";
            String score = report.getOverallScore() == null ? "评分待确认" : "综合匹配 " + report.getOverallScore() + " 分";
            String fallbackNote = matchRawFallback(report) || TRUST_FALLBACK.equals(matchRawTrustStatus(report))
                    ? " · 当前资料不完整，建议复核后使用" : "";
            String schemaNote = hasMatchSchemaWarnings(report) ? " · 部分内容已整理，部分来源待复核" : "";
            return context + " · " + jdAnalysis + " · " + aiLog + " · " + score + fallbackNote + schemaNote;
        }
        return context + " · " + jdAnalysis + " · 匹配报告正在生成，来源待确认。";
    }

    private String matchRawTrustStatus(ResumeJobMatchReport report) {
        JsonNode rawResult = rawResultJsonOrNull(report);
        if (rawResult == null) {
            return null;
        }
        String value = normalizeTrustStatus(textValue(rawResult, "trustStatus"));
        if (TRUST_PARTIAL.equals(value) || TRUST_VERIFIED.equals(value) || TRUST_FALLBACK.equals(value)) {
            return value;
        }
        return null;
    }

    private boolean matchRawFallback(ResumeJobMatchReport report) {
        JsonNode rawResult = rawResultJsonOrNull(report);
        return rawResult != null && rawResult.path("fallback").asBoolean(false);
    }

    private boolean hasMatchSchemaWarnings(ResumeJobMatchReport report) {
        return matchSchemaWarningCount(report) > 0;
    }

    private JsonNode matchSchemaWarnings(ResumeJobMatchReport report) {
        JsonNode rawResult = rawResultJsonOrNull(report);
        if (rawResult == null || !rawResult.path("schemaWarnings").isArray()) {
            return objectMapper.createArrayNode();
        }
        return rawResult.path("schemaWarnings");
    }

    private JsonNode mergeSchemaWarnings(JsonNode rawWarnings, ArrayNode storedFieldWarnings) {
        if (storedFieldWarnings == null || storedFieldWarnings.isEmpty()) {
            return rawWarnings == null || !rawWarnings.isArray() ? objectMapper.createArrayNode() : rawWarnings;
        }
        ArrayNode merged = objectMapper.createArrayNode();
        if (rawWarnings != null && rawWarnings.isArray()) {
            rawWarnings.forEach(merged::add);
        }
        storedFieldWarnings.forEach(merged::add);
        return merged;
    }

    private int matchSchemaWarningCount(ResumeJobMatchReport report) {
        JsonNode warnings = matchSchemaWarnings(report);
        return warnings.isArray() ? warnings.size() : 0;
    }

    private String failedMatchDiagnosticJson(RuntimeException ex) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("trustStatus", TRUST_FALLBACK);
        root.put("fallback", true);
        root.put("status", ResumeJobMatchStatus.FAILED.getCode());
        ObjectNode diagnostic = objectMapper.createObjectNode();
        diagnostic.put("category", matchFailureCategory(ex));
        diagnostic.put("message", safeDiagnosticText(ex == null ? null : ex.getMessage()));
        root.set("errorDiagnostic", diagnostic);
        ArrayNode warnings = objectMapper.createArrayNode();
        markStoredSchemaWarning(warnings, "resumeJobMatchResult", "generation failed before trusted match result");
        root.set("schemaWarnings", warnings);
        return root.toString();
    }

    private String matchFailureCategory(RuntimeException ex) {
        String message = ex == null ? "" : firstText(ex.getMessage(), "");
        String lower = message.toLowerCase();
        if (lower.contains("jd analysis") || lower.contains("job description") || lower.contains("target job jd")) {
            return "JD_ANALYSIS_REQUIRED";
        }
        if (lower.contains("unsupported fact") || lower.contains("missing evidence")) {
            return "EVIDENCE_MISMATCH";
        }
        if (lower.contains("not valid json") || lower.contains("must be a json object") || lower.contains("parse")) {
            return "AI_JSON_INVALID";
        }
        if (lower.contains("ai resume job match") || lower.contains("ai response") || lower.contains("techstack")) {
            return "AI_SCHEMA_INCOMPLETE";
        }
        return "MATCH_CONTEXT_INCOMPLETE";
    }

    private String safeDiagnosticText(String message) {
        if (!StringUtils.hasText(message)) {
            return "no diagnostic message";
        }
        String text = message.trim()
                .replaceAll("(?i)(api[_-]?key|token|authorization|password|secret)\\s*[:=]\\s*\\S+", "$1=***")
                .replaceAll("\\s+", " ");
        return text.length() <= 240 ? text : text.substring(0, 240);
    }

    private boolean isFallbackMatchResult(JsonNode result) {
        if (result == null) {
            return false;
        }
        return result.path("fallback").asBoolean(false)
                || TRUST_FALLBACK.equals(normalizeTrustStatus(textValue(result, "trustStatus")));
    }

    private String normalizeTrustStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private JsonNode rawResultJsonOrNull(ResumeJobMatchReport report) {
        if (report == null || !StringUtils.hasText(report.getRawResultJson())) {
            return null;
        }
        try {
            return objectMapper.readTree(report.getRawResultJson());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> resumeSnapshot(Resume resume, List<ResumeProject> projects) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", resume.getTitle());
        snapshot.put("realName", resume.getRealName());
        snapshot.put("targetPosition", resume.getTargetPosition());
        snapshot.put("skillStack", resume.getSkillStack());
        snapshot.put("workExperience", resume.getWorkExperience());
        snapshot.put("educationExperience", resume.getEducationExperience());
        snapshot.put("summary", resume.getSummary());
        snapshot.put("projects", projects.stream().map(this::projectSnapshot).toList());
        return snapshot;
    }

    private Map<String, Object> projectSnapshot(ResumeProject project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectName", project.getProjectName());
        snapshot.put("projectPeriod", project.getProjectPeriod());
        snapshot.put("projectBackground", project.getProjectBackground());
        snapshot.put("role", project.getRole());
        snapshot.put("techStack", project.getTechStack());
        snapshot.put("responsibility", project.getResponsibility());
        snapshot.put("coreFeatures", project.getCoreFeatures());
        snapshot.put("technicalDifficulties", project.getTechnicalDifficulties());
        snapshot.put("optimizationResults", project.getOptimizationResults());
        snapshot.put("description", project.getDescription());
        snapshot.put("highlights", project.getHighlights());
        return snapshot;
    }

    private String resumeSnapshotJson(MatchContext context) {
        if (context.resumeVersion() == null) {
            return toJson(resumeSnapshot(context.resume(), context.projects()));
        }
        ObjectNode snapshot = objectMapper.createObjectNode();
        JsonNode versionSnapshot = readResumeVersionSnapshot(context.resumeVersion());
        if (versionSnapshot != null && versionSnapshot.isObject()) {
            snapshot.setAll((ObjectNode) versionSnapshot.deepCopy());
        } else if (versionSnapshot != null) {
            snapshot.set("snapshot", versionSnapshot);
        }
        snapshot.put("snapshotSource", "RESUME_VERSION");
        snapshot.put("resumeVersionId", context.resumeVersion().getId());
        snapshot.put("resumeVersionNo", context.resumeVersion().getVersionNo());
        snapshot.put("resumeVersionName", context.resumeVersion().getVersionName());
        if (!snapshot.has("projects") || !snapshot.path("projects").isArray()) {
            snapshot.set("projects", objectMapper.valueToTree(
                    context.projects().stream().map(this::projectSnapshot).toList()));
            snapshot.put("projectSnapshotSource", "CURRENT_RESUME_PROJECTS");
        } else if (!StringUtils.hasText(snapshot.path("projectSnapshotSource").asText(null))) {
            snapshot.put("projectSnapshotSource", "RESUME_VERSION");
        }
        return snapshot.toString();
    }

    private String userExperienceYears(MatchContext context) {
        if (context.resumeVersion() != null) {
            JsonNode snapshot = readResumeVersionSnapshot(context.resumeVersion());
            String workExperience = textValue(snapshot, "workExperience");
            if (StringUtils.hasText(workExperience)) {
                return workExperience;
            }
        }
        return context.resume().getWorkExperience();
    }

    private JsonNode readResumeVersionSnapshot(ResumeVersion version) {
        if (version == null) {
            return null;
        }
        if (!StringUtils.hasText(version.getSnapshotJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历版本快照缺失，请重新创建版本后再生成匹配报告");
        }
        try {
            return objectMapper.readTree(version.getSnapshotJson());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历版本快照格式异常，请重新创建版本后再生成匹配报告");
        }
    }

    private boolean resumeVersionSnapshotHasProjects(ResumeVersion version) {
        JsonNode snapshot = readResumeVersionSnapshot(version);
        return snapshot != null && snapshot.path("projects").isArray();
    }

    private Map<String, Object> targetJobSnapshot(TargetJob job) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("jobTitle", job.getJobTitle());
        snapshot.put("companyName", job.getCompanyName());
        snapshot.put("jobLevel", job.getJobLevel());
        snapshot.put("jdSource", job.getJdSource());
        return snapshot;
    }

    private Map<String, Object> jobDescriptionSnapshot(JobDescriptionAnalysis analysis) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("jobTitle", analysis.getJobTitle());
        snapshot.put("companyName", analysis.getCompanyName());
        snapshot.put("jobLevel", analysis.getJobLevel());
        snapshot.put("responsibilities", readJsonOrNull(analysis.getResponsibilitiesJson()));
        snapshot.put("requiredSkills", readJsonOrNull(analysis.getRequiredSkillsJson()));
        snapshot.put("bonusSkills", readJsonOrNull(analysis.getBonusSkillsJson()));
        snapshot.put("techStackKeywords", readJsonOrNull(analysis.getTechKeywordsJson()));
        snapshot.put("businessKeywords", readJsonOrNull(analysis.getBusinessKeywordsJson()));
        snapshot.put("experienceRequirement", analysis.getExperienceRequirement());
        snapshot.put("projectExperienceRequirement", analysis.getProjectExperienceRequirement());
        snapshot.put("interviewFocusPoints", readJsonOrNull(analysis.getInterviewFocusJson()));
        snapshot.put("skillWeights", readJsonOrNull(analysis.getSkillWeightsJson()));
        snapshot.put("summary", analysis.getSummary());
        return snapshot;
    }

    private JsonNode parseResultJson(String resultJson) {
        JsonNode root;
        try {
            root = readJsonOrNull(resultJson);
        } catch (RuntimeException ex) {
            return fallbackStoredMatchResult("AI 返回内容暂时无法整理为匹配报告，系统已生成待复核记录。", ex);
        }
        JsonNode unwrapped = unwrapStoredMatchRoot(root);
        if (unwrapped instanceof ObjectNode) {
            return unwrapped;
        }
        ObjectNode fallback = fallbackStoredMatchResult("AI 返回结果格式不完整，系统已生成待复核记录。", null);
        markStoredSchemaWarning(ensureSchemaWarningsArray(fallback), "resultJson",
                "result root is not object, fallback report generated");
        return fallback;
    }

    private JsonNode unwrapStoredMatchRoot(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        JsonNode parsedText = parseTextualStoredJsonNode(json);
        if (parsedText != json) {
            return unwrapStoredMatchRoot(parsedText);
        }
        if (json.isArray() && json.size() == 1) {
            return unwrapStoredMatchRoot(json.get(0));
        }
        if (!json.isObject() || looksLikeStoredMatchResult(json)) {
            return json;
        }
        JsonNode wrapper = firstPresent(json, "resultJson", "result", "data", "report", "matchReport",
                "resumeJobMatch", "resumeJobMatchReport", "matchResult", "analysisResult", "content");
        if (wrapper == null) {
            return json;
        }
        JsonNode parsedWrapper = parseTextualStoredJsonNode(wrapper);
        JsonNode candidate = parsedWrapper != wrapper ? parsedWrapper : wrapper;
        return candidate == json ? json : unwrapStoredMatchRoot(candidate);
    }

    private JsonNode parseTextualStoredJsonNode(JsonNode node) {
        if (node == null || !node.isTextual() || !StringUtils.hasText(node.asText(null))) {
            return node;
        }
        try {
            return objectMapper.readTree(extractEmbeddedJson(node.asText()));
        } catch (Exception ignored) {
            return node;
        }
    }

    private boolean looksLikeStoredMatchResult(JsonNode json) {
        return json != null && json.isObject()
                && firstPresent(json, "overallScore", "overall_score", "matchScore", "match_score",
                "matchingScore", "score", "dimensionScores", "scores", "scoreDetails", "strengths",
                "advantages", "highlights", "gaps", "weaknesses", "skillGaps", "missingSkills",
                "summary", "overallSummary", "matchSummary", "整体评价", "匹配结论") != null;
    }

    private String extractEmbeddedJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String text = raw.trim();
        int codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int firstLineEnd = text.indexOf('\n', codeStart);
            int codeEnd = text.lastIndexOf("```");
            if (firstLineEnd >= 0 && codeEnd > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, codeEnd).trim();
            }
        }
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }
        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1);
        }
        return text;
    }

    private ObjectNode fallbackStoredMatchResult(String summary, RuntimeException ex) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("overallScore", 0);
        ObjectNode dimensionScores = objectMapper.createObjectNode();
        for (String dimension : List.of("techStack", "projectExperience", "businessFit", "communication")) {
            dimensionScores.put(dimension, 0);
        }
        root.set("dimensionScores", dimensionScores);
        root.set("strengths", objectMapper.createArrayNode());
        ArrayNode gaps = objectMapper.createArrayNode();
        ObjectNode gap = objectMapper.createObjectNode();
        gap.put("skillName", "待复核匹配结论");
        gap.put("severity", "HIGH");
        gap.put("description", firstText(summary, "匹配报告资料来源不完整，请重新生成或补齐资料后复核。"));
        gap.put("evidence", "AI 输出未通过结构化整理，系统已将异常内容标记为待复核。");
        ArrayNode actions = objectMapper.createArrayNode();
        actions.add("检查简历项目经历、目标岗位分析结果后重新生成匹配报告。");
        actions.add("在任务中心查看本次处理进度，确认是否需要补充简历项目经历或岗位描述。");
        gap.set("recommendedActions", actions);
        gaps.add(gap);
        root.set("gaps", gaps);
        root.set("resumeRisks", objectMapper.createArrayNode());
        root.set("optimizationSuggestions", objectMapper.createArrayNode()
                .add("当前资料来源不完整，建议先补充简历项目经历、岗位描述和关键技能后重新生成。"));
        root.set("recommendedLearningTopics", objectMapper.createArrayNode()
                .add("先练习目标岗位核心 Java 后端基础与项目表达。"));
        root.set("recommendedInterviewTopics", objectMapper.createArrayNode()
                .add("围绕当前简历项目准备 1 场基础追问面试。"));
        root.put("summary", firstText(summary, "匹配报告资料来源不完整，请重新生成或补齐资料后复核。"));
        root.put("trustStatus", TRUST_FALLBACK);
        root.put("fallback", true);
        ArrayNode warnings = objectMapper.createArrayNode();
        markStoredSchemaWarning(warnings, "resultJson",
                firstText(ex == null ? null : safeDiagnosticText(ex.getMessage()),
                        "AI result could not be normalized, fallback report generated"));
        root.set("schemaWarnings", warnings);
        return root;
    }

    private ObjectNode normalizeStoredMatchResult(JsonNode resultJson) {
        if (!(resultJson instanceof ObjectNode root)) {
            throw invalidMatchResult("匹配结果格式异常");
        }
        ObjectNode normalized = root.deepCopy();
        ArrayNode schemaWarnings = normalized.path("schemaWarnings").isArray()
                ? ((ArrayNode) normalized.path("schemaWarnings")).deepCopy()
                : objectMapper.createArrayNode();

        Integer overallScore = readScore(firstPresent(normalized, "overallScore", "overall_score",
                "matchScore", "match_score", "matchingScore", "matching_score", "score", "totalScore",
                "total_score", "综合得分", "匹配分", "综合匹配度"));
        if (overallScore == null) {
            overallScore = averageDimensionScore(firstPresent(normalized, "dimensionScores", "scores",
                    "scoreDetails", "dimensionScore", "dimension_score", "维度得分", "维度评分"));
        }
        normalized.put("overallScore", clampScore(overallScore == null ? 0 : overallScore));

        JsonNode rawDimensionScores = firstPresent(normalized, "dimensionScores", "scores", "scoreDetails",
                "dimensionScore", "dimension_score", "维度得分", "维度评分");
        ObjectNode dimensionScores = rawDimensionScores != null && rawDimensionScores.isObject()
                ? ((ObjectNode) rawDimensionScores).deepCopy()
                : objectMapper.createObjectNode();
        for (String dimension : List.of("techStack", "projectExperience", "businessFit", "communication")) {
            String[] scoreFields = resumeMatchDimensionScoreFields(dimension);
            Integer score = readScore(firstPresent(dimensionScores, scoreFields));
            if (score == null) {
                score = readDimensionScore(rawDimensionScores, dimension);
            }
            if (score == null) {
                score = readScore(firstPresent(normalized, scoreFields));
            }
            dimensionScores.put(dimension, clampScore(score == null ? normalized.path("overallScore").asInt(0) : score));
        }
        normalized.set("dimensionScores", dimensionScores);

        normalizeStoredArrayField(normalized, "strengths", true, "advantages", "highlights", "matchStrengths",
                "matchedPoints", "matchedSkills", "优势", "匹配优势", "亮点");
        normalizeStoredArrayField(normalized, "gaps", true, "weaknesses", "skillGaps", "mismatchPoints",
                "missingSkills", "gapItems", "shortcomings", "差距", "短板", "能力短板", "不足项");
        normalizeStoredArrayField(normalized, "resumeRisks", false, "risks", "riskPoints", "resumeIssues",
                "riskWarnings", "风险", "简历风险");
        normalizeStoredArrayField(normalized, "optimizationSuggestions", false,
                "suggestions", "improvementSuggestions", "resumeOptimizationSuggestions",
                "recommendations", "nextSteps", "优化建议", "改进建议");
        normalizeStoredArrayField(normalized, "recommendedLearningTopics", true,
                "learningTopics", "studyTopics", "recommendedSkills",
                "learningSuggestions", "studyRecommendations", "学习主题", "学习建议");
        normalizeStoredArrayField(normalized, "recommendedInterviewTopics", true,
                "interviewTopics", "practiceTopics", "recommendedQuestions",
                "interviewSuggestions", "interviewFocus", "面试主题", "面试建议", "面试重点");
        normalizeStoredStrengthItems(normalized, schemaWarnings);
        normalizeStoredGapItems(normalized, schemaWarnings);

        if (!StringUtils.hasText(textValue(normalized, "summary"))) {
            markStoredSchemaWarning(schemaWarnings, "summary", "missing summary filled");
            String summary = textValue(normalized, "overallSummary", "matchSummary", "comment",
                    "conclusion", "overallEvaluation", "summaryText", "整体评价", "匹配结论");
            normalized.put("summary", firstText(summary,
                    "AI 已返回部分匹配结果，系统已按可用内容生成待复核报告，请结合明细复核。"));
        }
        if (!schemaWarnings.isEmpty()) {
            normalized.set("schemaWarnings", schemaWarnings);
            if (!StringUtils.hasText(textValue(normalized, "trustStatus"))) {
                normalized.put("trustStatus", TRUST_PARTIAL);
            }
        } else if (!StringUtils.hasText(textValue(normalized, "trustStatus"))) {
            normalized.put("trustStatus", TRUST_VERIFIED);
        }
        return normalized;
    }

    private void normalizeStoredArrayField(ObjectNode root, String fieldName, boolean allowTextItem, String... aliases) {
        JsonNode node = firstPresent(root, joinFieldNames(fieldName, aliases));
        if (node != null && node.isArray()) {
            root.set(fieldName, node);
            return;
        }
        ArrayNode array = objectMapper.createArrayNode();
        if (node != null && node.isObject()) {
            array.add(node);
        } else if (node != null && allowTextItem && StringUtils.hasText(node.asText(null))) {
            array.add(node.asText());
        }
        root.set(fieldName, array);
    }

    private void normalizeStoredStrengthItems(ObjectNode root, ArrayNode schemaWarnings) {
        JsonNode strengths = root.path("strengths");
        ArrayNode normalized = objectMapper.createArrayNode();
        if (strengths.isArray()) {
            int index = 0;
            for (JsonNode item : strengths) {
                ObjectNode normalizedItem = normalizeStoredStrengthItem(item, schemaWarnings, index);
                if (normalizedItem != null) {
                    normalized.add(normalizedItem);
                }
                index++;
            }
        }
        root.set("strengths", normalized);
    }

    private ObjectNode normalizeStoredStrengthItem(JsonNode item, ArrayNode schemaWarnings, int index) {
        String fieldPath = "strengths[" + index + "]";
        ObjectNode normalized = item instanceof ObjectNode objectItem
                ? objectItem.deepCopy()
                : objectMapper.createObjectNode();
        String title = item != null && item.isObject()
                ? textValue(item, "title", "name", "point", "summary", "advantage", "strength")
                : item == null ? null : item.asText(null);
        String evidence = item != null && item.isObject()
                ? textValue(item, "evidence", "reason", "basis", "source", "detail", "description")
                : null;
        if (!StringUtils.hasText(title) && !StringUtils.hasText(evidence)) {
            markStoredSchemaWarning(schemaWarnings, fieldPath, "empty strength item skipped");
            return null;
        }
        if (!StringUtils.hasText(title)) {
            markStoredSchemaWarning(schemaWarnings, fieldPath + ".title", "missing title filled");
            title = "待复核匹配优势";
        }
        if (!StringUtils.hasText(evidence)) {
            markStoredSchemaWarning(schemaWarnings, fieldPath + ".evidence", "missing evidence marked for review");
            evidence = "AI 返回该优势但缺少结构化来源说明，已标记为待人工复核。";
        }
        normalized.put("title", title);
        normalized.put("evidence", evidence);
        normalized.set("relatedSkills", normalizeTextArray(firstPresent(normalized,
                "relatedSkills", "skills", "skillNames", "skill")));
        return normalized;
    }

    private void normalizeStoredGapItems(ObjectNode root, ArrayNode schemaWarnings) {
        JsonNode gaps = root.path("gaps");
        ArrayNode normalized = objectMapper.createArrayNode();
        if (gaps.isArray()) {
            int index = 0;
            for (JsonNode item : gaps) {
                ObjectNode normalizedItem = normalizeStoredGapItem(item, schemaWarnings, index);
                if (normalizedItem != null) {
                    normalized.add(normalizedItem);
                }
                index++;
            }
        }
        root.set("gaps", normalized);
    }

    private ObjectNode normalizeStoredGapItem(JsonNode item, ArrayNode schemaWarnings, int index) {
        String fieldPath = "gaps[" + index + "]";
        ObjectNode normalized = item instanceof ObjectNode objectItem
                ? objectItem.deepCopy()
                : objectMapper.createObjectNode();
        String textItem = item != null && item.isTextual() ? item.asText(null) : null;
        String skillName = item != null && item.isObject()
                ? textValue(item, "skillName", "skill", "knowledgePoint", "topic", "name")
                : null;
        String description = item != null && item.isObject()
                ? textValue(item, "description", "gapDescription", "weakness", "problem", "summary")
                : textItem;
        String evidence = item != null && item.isObject()
                ? textValue(item, "evidence", "reason", "basis", "source", "detail")
                : null;
        if (!StringUtils.hasText(skillName)
                && !StringUtils.hasText(description)
                && !StringUtils.hasText(evidence)) {
            markStoredSchemaWarning(schemaWarnings, fieldPath, "empty gap item skipped");
            return null;
        }
        if (!StringUtils.hasText(skillName)) {
            markStoredSchemaWarning(schemaWarnings, fieldPath + ".skillName", "missing skillName filled");
            skillName = "待确认技能";
        }
        if (!StringUtils.hasText(description)) {
            markStoredSchemaWarning(schemaWarnings, fieldPath + ".description", "missing description filled");
            description = "AI 返回该差距但缺少结构化描述，已标记为待人工复核。";
        }
        if (!StringUtils.hasText(evidence)) {
            markStoredSchemaWarning(schemaWarnings, fieldPath + ".evidence", "missing evidence marked for review");
            evidence = "AI 返回该差距但缺少结构化来源说明，已标记为待人工复核。";
        }
        normalized.put("skillName", skillName);
        normalized.put("description", description);
        normalized.put("evidence", evidence);
        normalized.put("severity", normalizeSeverity(textValue(normalized, "severity", "level", "priority", "gapSeverity")));
        normalized.set("recommendedActions", normalizeTextArray(firstPresent(normalized,
                "recommendedActions", "actions", "suggestions", "nextActions")));
        return normalized;
    }

    private Integer averageDimensionScore(JsonNode dimensionScores) {
        if (dimensionScores == null) {
            return null;
        }
        int total = 0;
        int count = 0;
        for (String dimension : List.of("techStack", "projectExperience", "businessFit", "communication")) {
            Integer score = dimensionScores.isObject()
                    ? readScore(firstPresent(dimensionScores, resumeMatchDimensionScoreFields(dimension)))
                    : readDimensionScore(dimensionScores, dimension);
            if (score != null) {
                total += clampScore(score);
                count++;
            }
        }
        return count == 0 ? null : Math.round((float) total / count);
    }

    private Integer readDimensionScore(JsonNode dimensionScores, String dimension) {
        if (dimensionScores == null || !dimensionScores.isArray()) {
            return null;
        }
        for (JsonNode item : dimensionScores) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String name = textValue(item, "dimension", "name", "label", "type", "category", "key", "skill");
            if (!matchesDimensionName(name, dimension)) {
                continue;
            }
            Integer score = readScore(firstPresent(item, "score", "value", "matchScore", "match_score",
                    "overallScore", "overall_score", "points", "得分", "分数"));
            if (score != null) {
                return score;
            }
        }
        return null;
    }

    private boolean matchesDimensionName(String value, String dimension) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(dimension)) {
            return false;
        }
        String normalizedValue = normalizeDimensionName(value);
        for (String candidate : resumeMatchDimensionScoreFields(dimension)) {
            if (normalizedValue.equals(normalizeDimensionName(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDimensionName(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[\\s_\\-:/：]+", "");
    }

    private String[] resumeMatchDimensionScoreFields(String dimension) {
        List<String> fields = new ArrayList<>();
        fields.add(dimension);
        fields.add(dimension + "Score");
        if ("techStack".equals(dimension)) {
            fields.addAll(List.of("tech_stack", "techStackScore", "technicalSkills", "technical_skills",
                    "techSkills", "tech_skills", "technology", "technologyScore", "technicalScore",
                    "skillScore", "skillsScore", "技术栈", "技术匹配", "技术能力"));
        } else if ("projectExperience".equals(dimension)) {
            fields.addAll(List.of("project_experience", "projectExperienceScore", "project", "projects",
                    "projectScore", "project_score", "experience", "experienceScore", "experience_score",
                    "项目经验", "项目经历", "项目匹配"));
        } else if ("businessFit".equals(dimension)) {
            fields.addAll(List.of("business_fit", "businessFitScore", "business", "domainFit", "domain_fit",
                    "jobFit", "job_fit", "positionFit", "position_fit", "businessScore",
                    "业务契合", "岗位契合", "业务匹配"));
        } else if ("communication".equals(dimension)) {
            fields.addAll(List.of("communication_score", "communicationSkill", "communication_skill",
                    "expression", "presentation", "communicationScore", "沟通表达", "表达能力", "沟通能力"));
        }
        return fields.toArray(String[]::new);
    }

    private int clampScore(Integer score) {
        return Math.max(0, Math.min(100, score == null ? 0 : score));
    }

    private String normalizeSeverity(String value) {
        if (!StringUtils.hasText(value)) {
            return "MEDIUM";
        }
        String upper = value.trim().toUpperCase();
        if (upper.contains("HIGH") || upper.contains("P0") || upper.contains("P1")) {
            return "HIGH";
        }
        if (upper.contains("LOW")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private ArrayNode normalizeTextArray(JsonNode node) {
        ArrayNode array = objectMapper.createArrayNode();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return array;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item == null || item.isNull()
                        ? null
                        : item.isValueNode() ? item.asText(null) : item.toString();
                if (StringUtils.hasText(text)) {
                    array.add(text);
                }
            }
            return array;
        }
        String text = node.isValueNode() ? node.asText(null) : node.toString();
        if (StringUtils.hasText(text)) {
            array.add(text);
        }
        return array;
    }

    private String[] joinFieldNames(String fieldName, String... aliases) {
        List<String> fieldNames = new ArrayList<>();
        fieldNames.add(fieldName);
        if (aliases != null) {
            fieldNames.addAll(List.of(aliases));
        }
        return fieldNames.toArray(String[]::new);
    }

    private JsonNode firstPresent(JsonNode json, String... fieldNames) {
        if (json == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode node = json.path(fieldName);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private void markStoredSchemaWarning(ArrayNode warnings, String fieldPath, String message) {
        if (warnings == null) {
            return;
        }
        ObjectNode warning = objectMapper.createObjectNode();
        warning.put("field", fieldPath);
        warning.put("message", message);
        warnings.add(warning);
    }

    private ArrayNode ensureSchemaWarningsArray(ObjectNode root) {
        JsonNode warnings = root.path("schemaWarnings");
        if (warnings.isArray()) {
            return (ArrayNode) warnings;
        }
        ArrayNode array = objectMapper.createArrayNode();
        root.set("schemaWarnings", array);
        return array;
    }

    private void validateSuccessResult(JsonNode root, JsonNode dimensionScores) {
        requireScore(root, "overallScore");
        if (dimensionScores == null || !dimensionScores.isObject()) {
            throw invalidMatchResult("missing dimensionScores object");
        }
        requireScore(dimensionScores, "techStack");
        requireScore(dimensionScores, "projectExperience");
        requireScore(dimensionScores, "businessFit");
        requireScore(dimensionScores, "communication");
        requireArray(root, "strengths");
        requireArray(root, "gaps");
        requireArray(root, "resumeRisks");
        requireArray(root, "optimizationSuggestions");
        requireArray(root, "recommendedLearningTopics");
        requireArray(root, "recommendedInterviewTopics");
        if (!StringUtils.hasText(textValue(root, "summary"))) {
            throw invalidMatchResult("missing summary");
        }
        requireEvidenceFields(root.path("strengths"), "strengths", "title", "evidence");
        requireEvidenceFields(root.path("gaps"), "gaps", "skillName", "severity", "description", "evidence");
    }

    private Integer requireScore(JsonNode json, String fieldName) {
        Integer score = readScore(json.path(fieldName));
        if (score == null || score < 0 || score > 100) {
            throw invalidMatchResult("invalid score " + fieldName);
        }
        return score;
    }

    private void requireArray(JsonNode json, String fieldName) {
        if (json == null || !json.path(fieldName).isArray()) {
            throw invalidMatchResult("missing array " + fieldName);
        }
    }

    private void requireEvidenceFields(JsonNode array, String arrayName, String... fields) {
        if (array == null || !array.isArray()) {
            throw invalidMatchResult("missing array " + arrayName);
        }
        for (JsonNode item : array) {
            if (!item.isObject()) {
                throw invalidMatchResult("invalid item in " + arrayName);
            }
            for (String field : fields) {
                if (!StringUtils.hasText(textValue(item, field))) {
                    throw invalidMatchResult("missing " + arrayName + "." + field);
                }
            }
        }
    }

    private BusinessException invalidMatchResult(String reason) {
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 简历匹配结果格式异常：" + reason);
    }

    private JsonNode readJsonOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "已保存的简历匹配结果格式异常");
        }
    }

    private ArrayNode readStoredReportArrayOrEmpty(String raw, String fieldName, ArrayNode warnings) {
        if (!StringUtils.hasText(raw)) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || node.isNull()) {
                return objectMapper.createArrayNode();
            }
            if (node.isArray()) {
                return (ArrayNode) node;
            }
            ArrayNode array = objectMapper.createArrayNode();
            array.add(node);
            markStoredSchemaWarning(warnings, fieldName, "stored field was not an array and was wrapped for display");
            return array;
        } catch (Exception ex) {
            markStoredSchemaWarning(warnings, fieldName, "stored field JSON was malformed and was hidden for display");
            return objectMapper.createArrayNode();
        }
    }

    private String jsonArrayText(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return "[]";
        }
        return value.isArray() ? value.toString() : objectMapper.createArrayNode().add(value.asText()).toString();
    }

    private Integer integerValue(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        return value.isNumber() ? value.asInt() : null;
    }

    private Integer readScore(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.asInt();
        }
        if (node.isNumber()) {
            return (int) Math.round(node.asDouble());
        }
        if (node.isTextual()) {
            Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(node.asText());
            if (matcher.find()) {
                try {
                    return (int) Math.round(Double.parseDouble(matcher.group()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        if (node.isObject()) {
            for (String field : List.of("score", "value", "totalScore", "matchScore")) {
                Integer score = readScore(node.path(field));
                if (score != null) {
                    return score;
                }
            }
        }
        return null;
    }

    private String textValue(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private String textValue(JsonNode json, String... fieldNames) {
        JsonNode value = firstPresent(json, fieldNames);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private String jsonOrText(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        return json.isTextual() ? json.asText() : json.toString();
    }

    private Integer scoreFromLevels(JsonNode gap) {
        Integer targetLevel = integerValue(gap, "targetLevel");
        Integer currentLevel = integerValue(gap, "currentLevel");
        if (targetLevel == null || currentLevel == null || targetLevel <= 0) {
            return null;
        }
        return Math.max(0, Math.min(100, currentLevel * 100 / targetLevel));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "结果序列化失败，请稍后重试");
        }
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private long normalizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String safeUserFacingMatchError(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "\u7b80\u5386\u5339\u914d\u62a5\u544a\u751f\u6210\u5931\u8d25\uff0c\u4efb\u52a1\u8bb0\u5f55\u5df2\u4fdd\u7559\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
        }
        String lower = message.toLowerCase();
        if (lower.contains("jd analysis") || lower.contains("job description") || lower.contains("target job jd")) {
            return "目标岗位描述尚未完成分析，请先重新分析岗位描述后再生成匹配报告。";
        }
        if (lower.contains("unsupported fact") || lower.contains("cannot be based on missing evidence")) {
            return "AI 返回内容与简历或岗位描述不一致，系统已阻止生成，建议补充资料后重新生成。";
        }
        if (lower.contains("not valid json") || lower.contains("must be a json object")
                || lower.contains("stored resume job match json is invalid")) {
            return "AI 返回内容暂时无法整理为匹配报告，已保留本次任务记录，请稍后重新生成。";
        }
        if (lower.contains("ai resume job match")
                || lower.contains("ai response")
                || lower.contains("techstack")
                || lower.contains("json")
                || lower.contains("parse")) {
            return "AI 匹配结果暂时不完整，已保留任务记录。建议先检查简历项目经历和岗位分析结果，再重新生成匹配报告。";
        }
        return "\u7b80\u5386\u6216\u76ee\u6807\u5c97\u4f4d\u6570\u636e\u4e0d\u5b8c\u6574\uff0c\u8bf7\u68c0\u67e5\u540e\u91cd\u65b0\u751f\u6210\u5339\u914d\u62a5\u544a\u3002";
    }

    private String userFacingMatchError(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "简历匹配报告生成失败，任务记录已保留，请稍后重试。";
        }
        String lower = message.toLowerCase();
        if (lower.contains("unsupported fact") || lower.contains("cannot be based on missing evidence")) {
            return "AI 返回内容与简历或岗位描述不一致，系统已阻止生成，建议补充资料后重新生成。";
        }
        if (lower.contains("not valid json") || lower.contains("must be a json object")) {
            return "AI 返回内容暂时无法整理为匹配报告，已保留本次任务记录，请稍后重新生成。";
        }
        if (lower.contains("ai resume job match")
                || lower.contains("ai response")
                || lower.contains("techstack")
                || lower.contains("json")
                || lower.contains("parse")) {
            return "AI 匹配结果暂时不完整，已保留任务记录。建议检查简历项目经历和岗位分析结果后重新生成。";
        }
        return "简历或目标岗位数据不完整，请检查后重新生成匹配报告。";
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

    private record MatchContext(Resume resume, ResumeVersion resumeVersion, List<ResumeProject> projects,
                                ResumeAnalysisRecord resumeAnalysis, TargetJob targetJob,
                                JobDescriptionAnalysis jdAnalysis) {
    }
}
