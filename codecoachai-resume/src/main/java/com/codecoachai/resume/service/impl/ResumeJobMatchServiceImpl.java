package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeJobMatchCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeJobMatchQueryDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeJobMatchDetail;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.vo.ResumeJobMatchDetailItemVO;
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
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.ResumeJobMatchMqDispatcher;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final ResumeJobMatchReportMapper reportMapper;
    private final ResumeJobMatchDetailMapper detailMapper;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Optional<ResumeJobMatchMqDispatcher> resumeJobMatchMqDispatcher;

    @Override
    public ResumeJobMatchSubmitVO createReport(ResumeJobMatchCreateDTO dto) {
        Long userId = requireCurrentUserId();
        MatchContext context = prepareContext(dto.getResumeId(), dto.getTargetJobId(), userId);
        if (!Boolean.TRUE.equals(dto.getForceRefresh())) {
            ResumeJobMatchReport existing = latestSuccessfulReport(dto.getResumeId(), dto.getTargetJobId(), userId);
            if (existing != null) {
                return toSubmitVO(existing);
            }
        }
        ResumeJobMatchReport report = transactionTemplate.execute(status -> createProcessingReport(context));
        if (dispatchAnalyze(report)) {
            return toSubmitVO(report);
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
    public ResumeJobMatchReportDetailVO getLatest(Long resumeId, Long targetJobId) {
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
        if (targetJobId != null) {
            getOwnedTargetJob(targetJobId, userId);
            wrapper.eq(ResumeJobMatchReport::getTargetJobId, targetJobId);
        }
        ResumeJobMatchReport report = reportMapper.selectOne(wrapper);
        return report == null ? null : toDetailVO(report);
    }

    @Override
    public ResumeJobMatchSubmitVO regenerate(Long id) {
        Long userId = requireCurrentUserId();
        ResumeJobMatchReport oldReport = getOwnedReport(id, userId);
        MatchContext context = prepareContext(oldReport.getResumeId(), oldReport.getTargetJobId(), userId);
        ResumeJobMatchReport newReport = transactionTemplate.execute(status -> createProcessingReport(context));
        if (dispatchAnalyze(newReport)) {
            return toSubmitVO(newReport);
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
            MatchContext context = prepareContext(report.getResumeId(), report.getTargetJobId(), report.getUserId());
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

    private boolean dispatchAnalyze(ResumeJobMatchReport report) {
        return report != null && resumeJobMatchMqDispatcher
                .map(dispatcher -> dispatcher.dispatchAnalyze(report.getId(), report.getUserId()))
                .orElse(false);
    }

    private MatchContext prepareContext(Long resumeId, Long targetJobId, Long userId) {
        Resume resume = getOwnedResume(resumeId, userId);
        List<ResumeProject> projects = projectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, resumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeProject::getSortOrder)
                .orderByAsc(ResumeProject::getSort)
                .orderByDesc(ResumeProject::getUpdatedAt));
        ResumeAnalysisRecord resumeAnalysis = latestSuccessfulResumeAnalysis(resumeId, userId);
        TargetJob targetJob = getOwnedTargetJob(targetJobId, userId);
        JobDescriptionAnalysis jdAnalysis = latestParsedJdAnalysis(targetJobId, userId);
        return new MatchContext(resume, projects, resumeAnalysis, targetJob, jdAnalysis);
    }

    private ResumeJobMatchReport createProcessingReport(MatchContext context) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setUserId(context.resume().getUserId());
        report.setResumeId(context.resume().getId());
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
        request.setTargetJobId(report.getTargetJobId());
        request.setJdAnalysisId(report.getJdAnalysisId());
        request.setResumeAnalysisJson(context.resumeAnalysis() == null ? null : context.resumeAnalysis().getStructuredJson());
        request.setResumeSnapshotJson(toJson(resumeSnapshot(context.resume(), context.projects())));
        request.setJobDescriptionAnalysisJson(toJson(jobDescriptionSnapshot(context.jdAnalysis())));
        request.setTargetJobJson(toJson(targetJobSnapshot(context.targetJob())));
        request.setUserExperienceYears(context.resume().getWorkExperience());
        return request;
    }

    private ResumeJobMatchReport markSuccess(Long reportId, JsonNode resultJson, Long aiCallLogId) {
        ResumeJobMatchReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume job match report missing");
        }
        JsonNode dimensionScores = resultJson.path("dimensionScores");
        validateSuccessResult(resultJson, dimensionScores);
        List<ResumeJobMatchDetail> details = buildDetails(report, resultJson);
        if (details.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI resume job match result invalid: missing detail evidence");
        }
        report.setOverallScore(integerValue(resultJson, "overallScore"));
        report.setTechStackScore(integerValue(dimensionScores, "techStack"));
        report.setProjectExperienceScore(integerValue(dimensionScores, "projectExperience"));
        report.setBusinessFitScore(integerValue(dimensionScores, "businessFit"));
        report.setCommunicationScore(integerValue(dimensionScores, "communication"));
        report.setStrengthsJson(jsonArrayText(resultJson, "strengths"));
        report.setGapsJson(jsonArrayText(resultJson, "gaps"));
        report.setResumeRisksJson(jsonArrayText(resultJson, "resumeRisks"));
        report.setOptimizationSuggestionsJson(jsonArrayText(resultJson, "optimizationSuggestions"));
        report.setRecommendedLearningTopicsJson(jsonArrayText(resultJson, "recommendedLearningTopics"));
        report.setRecommendedInterviewTopicsJson(jsonArrayText(resultJson, "recommendedInterviewTopics"));
        report.setSummary(textValue(resultJson, "summary"));
        report.setRawResultJson(resultJson.toString());
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
                ResumeJobMatchDetail detail = baseDetail(report);
                detail.setDimension(firstText(textValue(gap, "category"), "GAP"));
                detail.setSkillName(textValue(gap, "skillName"));
                detail.setMatchLevel(textValue(gap, "severity"));
                detail.setScore(scoreFromLevels(gap));
                detail.setEvidence(textValue(gap, "evidence"));
                detail.setGapDescription(textValue(gap, "description"));
                detail.setSuggestion(jsonOrText(gap.path("recommendedActions")));
                details.add(detail);
            }
        }
        JsonNode strengths = resultJson.path("strengths");
        if (strengths.isArray()) {
            for (JsonNode strength : strengths) {
                ResumeJobMatchDetail detail = baseDetail(report);
                detail.setDimension("STRENGTH");
                detail.setSkillName(jsonOrText(strength.path("relatedSkills")));
                detail.setMatchLevel("MATCHED");
                detail.setScore(null);
                detail.setEvidence(textValue(strength, "evidence"));
                detail.setGapDescription(null);
                detail.setSuggestion(textValue(strength, "title"));
                details.add(detail);
            }
        }
        return details;
    }

    private ResumeJobMatchDetail baseDetail(ResumeJobMatchReport report) {
        ResumeJobMatchDetail detail = new ResumeJobMatchDetail();
        detail.setReportId(report.getId());
        detail.setUserId(report.getUserId());
        return detail;
    }

    private Resume getOwnedResume(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resumeId is required");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, id)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume not found");
        }
        return resume;
    }

    private TargetJob getOwnedTargetJob(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "targetJobId is required");
        }
        TargetJob job = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, id)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (job == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target job not found");
        }
        return job;
    }

    private ResumeJobMatchReport getOwnedReport(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "reportId is required");
        }
        ResumeJobMatchReport report = reportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getId, id)
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume job match report not found");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target job JD analysis is not parsed");
        }
        return analysis;
    }

    private ResumeJobMatchReport latestSuccessfulReport(Long resumeId, Long targetJobId, Long userId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getResumeId, resumeId)
                .eq(ResumeJobMatchReport::getTargetJobId, targetJobId)
                .eq(ResumeJobMatchReport::getStatus, ResumeJobMatchStatus.SUCCESS.getCode())
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeJobMatchReport::getUpdatedAt)
                .last("limit 1"));
    }

    private ResumeJobMatchReportListVO toListVO(ResumeJobMatchReport report) {
        Resume resume = resumeMapper.selectById(report.getResumeId());
        TargetJob job = targetJobMapper.selectById(report.getTargetJobId());
        ResumeJobMatchReportListVO vo = new ResumeJobMatchReportListVO();
        vo.setReportId(report.getId());
        vo.setResumeId(report.getResumeId());
        vo.setResumeTitle(resume == null ? null : resume.getTitle());
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
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }

    private ResumeJobMatchReportDetailVO toDetailVO(ResumeJobMatchReport report) {
        Resume resume = resumeMapper.selectById(report.getResumeId());
        TargetJob job = targetJobMapper.selectById(report.getTargetJobId());
        ResumeJobMatchReportDetailVO vo = new ResumeJobMatchReportDetailVO();
        vo.setReportId(report.getId());
        vo.setUserId(report.getUserId());
        vo.setResumeId(report.getResumeId());
        vo.setResumeTitle(resume == null ? null : resume.getTitle());
        vo.setTargetJobId(report.getTargetJobId());
        vo.setJobTitle(job == null ? null : job.getJobTitle());
        vo.setCompanyName(job == null ? null : job.getCompanyName());
        vo.setJdAnalysisId(report.getJdAnalysisId());
        vo.setOverallScore(report.getOverallScore());
        vo.setTechStackScore(report.getTechStackScore());
        vo.setProjectExperienceScore(report.getProjectExperienceScore());
        vo.setBusinessFitScore(report.getBusinessFitScore());
        vo.setCommunicationScore(report.getCommunicationScore());
        vo.setStrengths(readJsonOrNull(report.getStrengthsJson()));
        vo.setGaps(readJsonOrNull(report.getGapsJson()));
        vo.setResumeRisks(readJsonOrNull(report.getResumeRisksJson()));
        vo.setOptimizationSuggestions(readJsonOrNull(report.getOptimizationSuggestionsJson()));
        vo.setRecommendedLearningTopics(readJsonOrNull(report.getRecommendedLearningTopicsJson()));
        vo.setRecommendedInterviewTopics(readJsonOrNull(report.getRecommendedInterviewTopicsJson()));
        vo.setSummary(report.getSummary());
        vo.setRawResult(readJsonOrNull(report.getRawResultJson()));
        vo.setStatus(report.getStatus());
        vo.setErrorMessage(report.getErrorMessage());
        vo.setAiCallLogId(report.getAiCallLogId());
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
        ResumeJobMatchSubmitVO vo = new ResumeJobMatchSubmitVO();
        vo.setReportId(report.getId());
        vo.setResumeId(report.getResumeId());
        vo.setTargetJobId(report.getTargetJobId());
        vo.setJdAnalysisId(report.getJdAnalysisId());
        vo.setAiCallLogId(report.getAiCallLogId());
        vo.setStatus(report.getStatus());
        vo.setErrorMessage(report.getErrorMessage());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
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
        JsonNode root = readJsonOrNull(resultJson);
        if (root == null || !root.isObject()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume job match result must be a JSON object");
        }
        return root;
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
        Integer score = integerValue(json, fieldName);
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
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "AI resume job match result invalid: " + reason);
    }

    private JsonNode readJsonOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Stored resume job match JSON is invalid");
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

    private String textValue(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON serialization failed");
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
            return "\u76ee\u6807\u5c97\u4f4d JD \u5c1a\u672a\u5b8c\u6210\u89e3\u6790\uff0c\u8bf7\u5148\u91cd\u65b0\u89e3\u6790\u5c97\u4f4d\u63cf\u8ff0\u540e\u518d\u751f\u6210\u5339\u914d\u62a5\u544a\u3002";
        }
        if (lower.contains("ai resume job match")
                || lower.contains("ai response")
                || lower.contains("techstack")
                || lower.contains("json")
                || lower.contains("parse")) {
            return "\u0041\u0049 \u5339\u914d\u7ed3\u679c\u5b57\u6bb5\u5f02\u5e38\uff0c\u5df2\u4fdd\u7559\u672c\u6b21\u4efb\u52a1\u8bb0\u5f55\uff0c\u8bf7\u7a0d\u540e\u91cd\u65b0\u751f\u6210\u3002";
        }
        return "\u7b80\u5386\u6216\u76ee\u6807\u5c97\u4f4d\u6570\u636e\u4e0d\u5b8c\u6574\uff0c\u8bf7\u68c0\u67e5\u540e\u91cd\u65b0\u751f\u6210\u5339\u914d\u62a5\u544a\u3002";
    }

    private String userFacingMatchError(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "AI 匹配报告生成失败，请稍后重试。";
        }
        String lower = message.toLowerCase();
        if (lower.contains("ai resume job match")
                || lower.contains("ai response")
                || lower.contains("techstack")
                || lower.contains("json")
                || lower.contains("parse")) {
            return "AI 匹配结果部分字段异常，已保留本次任务记录，请稍后重新生成。";
        }
        return "\u7b80\u5386\u5339\u914d\u62a5\u544a\u751f\u6210\u5931\u8d25\uff0c\u4efb\u52a1\u8bb0\u5f55\u5df2\u4fdd\u7559\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
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

    private record MatchContext(Resume resume, List<ResumeProject> projects, ResumeAnalysisRecord resumeAnalysis,
                                TargetJob targetJob, JobDescriptionAnalysis jdAnalysis) {
    }
}
