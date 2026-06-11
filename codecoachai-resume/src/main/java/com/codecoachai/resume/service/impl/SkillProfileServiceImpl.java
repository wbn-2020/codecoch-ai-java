package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.SkillProfileGenerateDTO;
import com.codecoachai.resume.domain.dto.InterviewWeakPointFeedbackDTO;
import com.codecoachai.resume.domain.dto.SkillProfileQueryDTO;
import com.codecoachai.resume.domain.dto.SkillProfileRefreshDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeJobMatchDetail;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.enums.SkillProfileStatus;
import com.codecoachai.resume.domain.vo.InnerSkillGapItemVO;
import com.codecoachai.resume.domain.vo.InnerSkillProfileVO;
import com.codecoachai.resume.domain.vo.SkillGapItemVO;
import com.codecoachai.resume.domain.vo.SkillProfileDetailVO;
import com.codecoachai.resume.domain.vo.SkillProfileGenerateVO;
import com.codecoachai.resume.domain.vo.SkillProfileListVO;
import com.codecoachai.resume.domain.vo.SkillProfileOverviewVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.dto.AnalyzeSkillGapDTO;
import com.codecoachai.resume.feign.vo.AnalyzeSkillGapVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchDetailMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.SkillGapItemMapper;
import com.codecoachai.resume.mapper.SkillProfileMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.SkillProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SkillProfileServiceImpl implements SkillProfileService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final long DEFAULT_PAGE_NO = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String SOURCE_RESUME_JOB_MATCH = "RESUME_JOB_MATCH";
    private static final String TRUST_FALLBACK = "FALLBACK";

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final ResumeJobMatchReportMapper reportMapper;
    private final ResumeJobMatchDetailMapper detailMapper;
    private final SkillProfileMapper profileMapper;
    private final SkillGapItemMapper gapItemMapper;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SkillProfileGenerateVO generate(SkillProfileGenerateDTO dto) {
        if (dto == null || dto.getMatchReportId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择匹配报告");
        }
        Long userId = requireCurrentUserId();
        return generateFromMatchReport(dto.getMatchReportId(), userId);
    }

    @Override
    public SkillProfileDetailVO getByTargetJob(Long targetJobId) {
        Long userId = requireCurrentUserId();
        getOwnedTargetJob(targetJobId, userId);
        SkillProfile profile = latestSuccessProfile(targetJobId, userId);
        return profile == null ? null : toDetailVO(profile);
    }

    @Override
    public SkillProfileDetailVO getById(Long profileId) {
        Long userId = requireCurrentUserId();
        return toDetailVO(getOwnedProfile(profileId, userId));
    }

    @Override
    public SkillProfileOverviewVO getOverview(Long targetJobId) {
        Long userId = requireCurrentUserId();
        Long resolvedTargetJobId = resolveOverviewTargetJobId(targetJobId, userId);
        if (resolvedTargetJobId == null) {
            return emptyOverview(null);
        }
        SkillProfile profile = latestSuccessProfile(resolvedTargetJobId, userId);
        return profile == null ? emptyOverview(resolvedTargetJobId) : toOverviewVO(profile);
    }

    @Override
    public PageResult<SkillProfileListVO> listProfiles(SkillProfileQueryDTO query) {
        Long userId = requireCurrentUserId();
        SkillProfileQueryDTO request = query == null ? new SkillProfileQueryDTO() : query;
        if (request.getTargetJobId() != null) {
            getOwnedTargetJob(request.getTargetJobId(), userId);
        }
        long pageNo = normalizePageNo(request.getPageNo());
        long pageSize = normalizePageSize(request.getPageSize());

        LambdaQueryWrapper<SkillProfile> wrapper = new LambdaQueryWrapper<SkillProfile>()
                .eq(SkillProfile::getUserId, userId)
                .eq(SkillProfile::getDeleted, CommonConstants.NO)
                .orderByDesc(SkillProfile::getCreatedAt);
        if (request.getTargetJobId() != null) {
            wrapper.eq(SkillProfile::getTargetJobId, request.getTargetJobId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(SkillProfile::getStatus, request.getStatus());
        }

        Page<SkillProfile> page = profileMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<SkillProfileListVO> records = page.getRecords().stream()
                .map(this::toListVO)
                .toList();
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public SkillProfileGenerateVO refresh(SkillProfileRefreshDTO dto) {
        if (dto == null || (dto.getProfileId() == null && dto.getMatchReportId() == null)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择能力画像或匹配报告");
        }
        Long userId = requireCurrentUserId();
        Long matchReportId = dto.getMatchReportId();
        if (matchReportId == null) {
            SkillProfile profile = getOwnedProfile(dto.getProfileId(), userId);
            matchReportId = profile.getMatchReportId();
        }
        return generateFromMatchReport(matchReportId, userId);
    }

    @Override
    public InnerSkillProfileVO getInnerProfile(Long profileId) {
        Long userId = requireCurrentUserId();
        SkillProfile profile = getOwnedProfile(profileId, userId);
        assertTrustedProfileEvidence(profile);
        return toInnerProfileVO(profile);
    }

    @Override
    public InnerSkillProfileVO getInnerSuccessProfileByMatchReport(Long matchReportId) {
        Long userId = requireCurrentUserId();
        ResumeJobMatchReport report = getOwnedReport(matchReportId, userId);
        if (!isTrustedSuccessMatchReport(report)) {
            return null;
        }
        SkillProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<SkillProfile>()
                .eq(SkillProfile::getMatchReportId, report.getId())
                .eq(SkillProfile::getUserId, userId)
                .eq(SkillProfile::getSourceType, SOURCE_RESUME_JOB_MATCH)
                .eq(SkillProfile::getStatus, SkillProfileStatus.SUCCESS.getCode())
                .eq(SkillProfile::getDeleted, CommonConstants.NO)
                .orderByDesc(SkillProfile::getUpdatedAt)
                .last("limit 1"));
        return profile == null ? null : toInnerProfileVO(profile);
    }

    @Override
    public void feedbackInterviewWeakPoints(InterviewWeakPointFeedbackDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getTargetJobId() == null
                || dto.getInterviewId() == null || dto.getWeakPoints() == null || dto.getWeakPoints().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "interview weak point feedback is incomplete");
        }
        TargetJob targetJob = targetJobMapper.selectById(dto.getTargetJobId());
        if (targetJob == null || !dto.getUserId().equals(targetJob.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不存在或已不可用");
        }
        SkillProfile profile = resolveFeedbackProfile(dto);
        int nextPriority = nextGapPriority(profile.getId(), dto.getUserId());
        for (String weakPoint : dto.getWeakPoints().stream().filter(StringUtils::hasText).limit(8).toList()) {
            SkillGapItem item = new SkillGapItem();
            item.setProfileId(profile.getId());
            item.setUserId(dto.getUserId());
            item.setTargetJobId(dto.getTargetJobId());
            item.setSkillName(summarize(weakPoint, 64));
            item.setCategory("INTERVIEW_FEEDBACK");
            item.setTargetLevel(4);
            item.setCurrentLevel(2);
            item.setGapLevel(2);
            item.setConfidence(new BigDecimal("0.70"));
            item.setSeverity("MEDIUM");
            item.setEvidenceSourcesJson(toJson(List.of("INTERVIEW_REPORT:" + dto.getReportId())));
            item.setGapDescription(weakPoint);
            item.setRecommendedActionsJson(toJson(List.of("结合面试报告复盘该弱项", "补充相关项目案例和原理细节", "完成针对性题目训练")));
            item.setPriority(nextPriority++);
            item.setSourceType("INTERVIEW_REPORT");
            item.setSourceBizId(dto.getInterviewId());
            gapItemMapper.insert(item);
        }
    }

    private SkillProfile resolveFeedbackProfile(InterviewWeakPointFeedbackDTO dto) {
        SkillProfile profile = null;
        if (dto.getSkillProfileId() != null) {
            profile = profileMapper.selectOne(new LambdaQueryWrapper<SkillProfile>()
                    .eq(SkillProfile::getId, dto.getSkillProfileId())
                    .eq(SkillProfile::getUserId, dto.getUserId())
                    .eq(SkillProfile::getDeleted, CommonConstants.NO)
                    .last("limit 1"));
        }
        if (profile == null && dto.getMatchReportId() != null) {
            profile = profileMapper.selectOne(new LambdaQueryWrapper<SkillProfile>()
                    .eq(SkillProfile::getMatchReportId, dto.getMatchReportId())
                    .eq(SkillProfile::getUserId, dto.getUserId())
                    .eq(SkillProfile::getDeleted, CommonConstants.NO)
                    .orderByDesc(SkillProfile::getUpdatedAt)
                    .last("limit 1"));
        }
        if (profile == null) {
            profile = latestSuccessProfile(dto.getTargetJobId(), dto.getUserId());
        }
        if (profile != null) {
            return profile;
        }
        SkillProfile created = new SkillProfile();
        created.setUserId(dto.getUserId());
        created.setTargetJobId(dto.getTargetJobId());
        created.setMatchReportId(dto.getMatchReportId());
        created.setProfileName("Interview feedback profile " + dto.getInterviewId());
        created.setSourceType("INTERVIEW_REPORT");
        created.setSourceBizId(dto.getInterviewId());
        created.setStatus(SkillProfileStatus.SUCCESS.getCode());
        created.setSummary("Generated from interview report weak-point feedback.");
        created.setOverallLevel(2);
        created.setOverallScore(60);
        profileMapper.insert(created);
        return created;
    }

    private int nextGapPriority(Long profileId, Long userId) {
        Long count = gapItemMapper.selectCount(new LambdaQueryWrapper<SkillGapItem>()
                .eq(SkillGapItem::getProfileId, profileId)
                .eq(SkillGapItem::getUserId, userId)
                .eq(SkillGapItem::getDeleted, CommonConstants.NO));
        return count == null ? 1 : count.intValue() + 1;
    }

    private String summarize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "Interview weak point";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private SkillProfileGenerateVO generateFromMatchReport(Long matchReportId, Long userId) {
        ResumeJobMatchReport report = getOwnedReport(matchReportId, userId);
        if (!isTrustedSuccessMatchReport(report)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Only trusted SUCCESS resume job match reports can generate skill profiles");
        }
        JsonNode reportGaps = readJsonOrNull(report.getGapsJson());
        if (!hasEffectiveGaps(reportGaps)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume job match report has no effective gaps");
        }

        SkillProfile profile = transactionTemplate.execute(status -> createProcessingProfile(report));
        try {
            SkillProfileContext context = prepareContext(report);
            AnalyzeSkillGapVO response = FeignResultUtils.unwrap(
                    aiFeignClient.analyzeSkillGap(toAiRequest(profile, context, reportGaps)));
            JsonNode resultJson = parseResultJson(response == null ? null : response.getResultJson());
            SkillProfile success = transactionTemplate.execute(status ->
                    markSuccess(profile.getId(), resultJson, response == null ? null : response.getAiCallLogId()));
            return toGenerateVO(success);
        } catch (RuntimeException ex) {
            SkillProfile failed = transactionTemplate.execute(status -> markFailed(profile.getId(), ex));
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "技能画像分析失败：" + failed.getErrorMessage());
        }
    }

    private SkillProfile createProcessingProfile(ResumeJobMatchReport report) {
        SkillProfile profile = new SkillProfile();
        profile.setUserId(report.getUserId());
        profile.setTargetJobId(report.getTargetJobId());
        profile.setMatchReportId(report.getId());
        profile.setProfileName("Skill profile from match report " + report.getId());
        profile.setSourceType(SOURCE_RESUME_JOB_MATCH);
        profile.setSourceBizId(report.getId());
        profile.setStatus(SkillProfileStatus.PROCESSING.getCode());
        profileMapper.insert(profile);
        return profile;
    }

    private SkillProfile markSuccess(Long profileId, JsonNode resultJson, Long aiCallLogId) {
        SkillProfile profile = profileMapper.selectById(profileId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Skill profile missing");
        }
        profile.setProfileName(firstText(textValue(resultJson, "profileName"), profile.getProfileName()));
        profile.setOverallLevel(integerValue(resultJson, "overallLevel"));
        profile.setOverallScore(integerValue(resultJson, "overallScore"));
        profile.setSummary(firstText(textValue(resultJson, "profileSummary"), textValue(resultJson, "summary")));
        profile.setStatus(SkillProfileStatus.SUCCESS.getCode());
        profile.setRawResultJson(resultJson.toString());
        profile.setAiCallLogId(aiCallLogId);
        profile.setErrorMessage(null);
        profileMapper.updateById(profile);
        replaceGapItems(profile, resultJson);
        return profileMapper.selectById(profileId);
    }

    private SkillProfile markFailed(Long profileId, RuntimeException ex) {
        SkillProfile profile = profileMapper.selectById(profileId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Skill profile missing");
        }
        profile.setStatus(SkillProfileStatus.FAILED.getCode());
        profile.setErrorMessage(truncateErrorMessage(ex == null ? null : ex.getMessage()));
        profileMapper.updateById(profile);
        gapItemMapper.delete(new LambdaQueryWrapper<SkillGapItem>()
                .eq(SkillGapItem::getProfileId, profileId)
                .eq(SkillGapItem::getUserId, profile.getUserId()));
        return profileMapper.selectById(profileId);
    }

    private void replaceGapItems(SkillProfile profile, JsonNode resultJson) {
        gapItemMapper.delete(new LambdaQueryWrapper<SkillGapItem>()
                .eq(SkillGapItem::getProfileId, profile.getId())
                .eq(SkillGapItem::getUserId, profile.getUserId()));
        JsonNode gaps = resultJson.path("skillGaps");
        if (!gaps.isArray()) {
            return;
        }
        int index = 1;
        for (JsonNode gap : gaps) {
            if (!StringUtils.hasText(textValue(gap, "skillName"))) {
                continue;
            }
            SkillGapItem item = new SkillGapItem();
            item.setProfileId(profile.getId());
            item.setUserId(profile.getUserId());
            item.setTargetJobId(profile.getTargetJobId());
            item.setSkillName(textValue(gap, "skillName"));
            item.setCategory(textValue(gap, "category"));
            item.setTargetLevel(integerValue(gap, "targetLevel"));
            item.setCurrentLevel(integerValue(gap, "currentLevel"));
            item.setGapLevel(firstInteger(integerValue(gap, "gapLevel"), gapLevel(item)));
            item.setConfidence(decimalValue(gap, "confidence"));
            item.setSeverity(firstText(textValue(gap, "severity"), severityFromGapLevel(item.getGapLevel())));
            item.setEvidenceSourcesJson(jsonArrayText(gap, "evidenceSources"));
            item.setGapDescription(firstText(textValue(gap, "gapDescription"), textValue(gap, "description")));
            item.setRecommendedActionsJson(jsonArrayText(gap, "recommendedActions"));
            item.setPriority(firstInteger(integerValue(gap, "priority"), index));
            item.setSourceType(SOURCE_RESUME_JOB_MATCH);
            item.setSourceBizId(profile.getMatchReportId());
            gapItemMapper.insert(item);
            index++;
        }
    }

    private SkillProfileContext prepareContext(ResumeJobMatchReport report) {
        Resume resume = resumeMapper.selectById(report.getResumeId());
        List<ResumeProject> projects = resume == null ? List.of() : projectMapper.selectList(
                new LambdaQueryWrapper<ResumeProject>()
                        .eq(ResumeProject::getResumeId, report.getResumeId())
                        .eq(ResumeProject::getDeleted, CommonConstants.NO)
                        .orderByAsc(ResumeProject::getSortOrder)
                        .orderByAsc(ResumeProject::getSort)
                        .orderByDesc(ResumeProject::getUpdatedAt));
        ResumeAnalysisRecord resumeAnalysis = latestSuccessfulResumeAnalysis(report.getResumeId(), report.getUserId());
        TargetJob targetJob = getOwnedTargetJob(report.getTargetJobId(), report.getUserId());
        JobDescriptionAnalysis jdAnalysis = jobDescriptionAnalysisMapper.selectById(report.getJdAnalysisId());
        List<ResumeJobMatchDetail> details = detailMapper.selectList(new LambdaQueryWrapper<ResumeJobMatchDetail>()
                .eq(ResumeJobMatchDetail::getReportId, report.getId())
                .eq(ResumeJobMatchDetail::getUserId, report.getUserId())
                .eq(ResumeJobMatchDetail::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeJobMatchDetail::getId));
        return new SkillProfileContext(resume, projects, resumeAnalysis, targetJob, jdAnalysis, report, details);
    }

    private AnalyzeSkillGapDTO toAiRequest(SkillProfile profile, SkillProfileContext context, JsonNode reportGaps) {
        AnalyzeSkillGapDTO request = new AnalyzeSkillGapDTO();
        request.setProfileId(profile.getId());
        request.setMatchReportId(context.report().getId());
        request.setUserId(context.report().getUserId());
        request.setResumeId(context.report().getResumeId());
        request.setTargetJobId(context.report().getTargetJobId());
        request.setJdAnalysisId(context.report().getJdAnalysisId());
        request.setTargetJobJson(toJson(targetJobSnapshot(context.targetJob())));
        request.setJobDescriptionAnalysisJson(toJson(jobDescriptionSnapshot(context.jdAnalysis())));
        request.setMatchReportJson(toJson(matchReportSnapshot(context.report())));
        request.setMatchDetailsJson(toJson(context.details().stream().map(this::matchDetailSnapshot).toList()));
        request.setGapsJson(reportGaps == null ? "[]" : reportGaps.toString());
        request.setRecommendedLearningTopicsJson(firstText(context.report().getRecommendedLearningTopicsJson(), "[]"));
        request.setRecommendedInterviewTopicsJson(firstText(context.report().getRecommendedInterviewTopicsJson(), "[]"));
        request.setResumeAnalysisJson(context.resumeAnalysis() == null ? null : context.resumeAnalysis().getStructuredJson());
        request.setResumeSnapshotJson(toJson(resumeSnapshot(context.resume(), context.projects())));
        return request;
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

    private SkillProfile getOwnedProfile(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择能力画像");
        }
        SkillProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<SkillProfile>()
                .eq(SkillProfile::getId, id)
                .eq(SkillProfile::getUserId, userId)
                .eq(SkillProfile::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (profile == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "技能画像不存在或已不可用");
        }
        return profile;
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

    private SkillProfile latestSuccessProfile(Long targetJobId, Long userId) {
        List<SkillProfile> profiles = profileMapper.selectList(new LambdaQueryWrapper<SkillProfile>()
                .eq(SkillProfile::getTargetJobId, targetJobId)
                .eq(SkillProfile::getUserId, userId)
                .eq(SkillProfile::getStatus, SkillProfileStatus.SUCCESS.getCode())
                .eq(SkillProfile::getDeleted, CommonConstants.NO)
                .orderByDesc(SkillProfile::getUpdatedAt)
                .last("limit 10"));
        return profiles.stream()
                .filter(this::hasTrustedProfileEvidence)
                .findFirst()
                .orElse(null);
    }

    private void assertTrustedProfileEvidence(SkillProfile profile) {
        if (!hasTrustedProfileEvidence(profile)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Skill profile is linked to a non-SUCCESS resume job match report");
        }
    }

    private boolean hasTrustedProfileEvidence(SkillProfile profile) {
        if (!isResumeJobMatchProfile(profile)) {
            return true;
        }
        if (profile.getMatchReportId() == null) {
            return false;
        }
        ResumeJobMatchReport report = reportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getId, profile.getMatchReportId())
                .eq(ResumeJobMatchReport::getUserId, profile.getUserId())
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        return isTrustedSuccessMatchReport(report);
    }

    private boolean isTrustedSuccessMatchReport(ResumeJobMatchReport report) {
        return report != null
                && ResumeJobMatchStatus.SUCCESS.getCode().equals(report.getStatus())
                && !isFallbackMatchReport(report);
    }

    private boolean isFallbackMatchReport(ResumeJobMatchReport report) {
        JsonNode rawResult = readJsonSafely(report == null ? null : report.getRawResultJson());
        return rawResult != null
                && (rawResult.path("fallback").asBoolean(false)
                || TRUST_FALLBACK.equalsIgnoreCase(rawResult.path("trustStatus").asText(null))
                || (rawResult.path("schemaWarnings").isArray() && !rawResult.path("schemaWarnings").isEmpty()));
    }

    private boolean isResumeJobMatchProfile(SkillProfile profile) {
        return profile != null && SOURCE_RESUME_JOB_MATCH.equalsIgnoreCase(String.valueOf(profile.getSourceType()));
    }

    private Long resolveOverviewTargetJobId(Long targetJobId, Long userId) {
        if (targetJobId != null) {
            getOwnedTargetJob(targetJobId, userId);
            return targetJobId;
        }
        TargetJob current = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getCurrentFlag, CommonConstants.YES)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .orderByDesc(TargetJob::getUpdatedAt)
                .last("limit 1"));
        return current == null ? null : current.getId();
    }

    private SkillProfileDetailVO toDetailVO(SkillProfile profile) {
        SkillProfileDetailVO vo = new SkillProfileDetailVO();
        vo.setProfileId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setTargetJobId(profile.getTargetJobId());
        vo.setMatchReportId(profile.getMatchReportId());
        vo.setProfileName(profile.getProfileName());
        vo.setOverallLevel(profile.getOverallLevel());
        vo.setOverallScore(profile.getOverallScore());
        vo.setSummary(profile.getSummary());
        vo.setSourceType(profile.getSourceType());
        vo.setSourceBizId(profile.getSourceBizId());
        vo.setStatus(profile.getStatus());
        vo.setErrorMessage(profile.getErrorMessage());
        vo.setAiCallLogId(profile.getAiCallLogId());
        vo.setGapItems(listGapItems(profile));
        vo.setCreatedAt(profile.getCreatedAt());
        vo.setUpdatedAt(profile.getUpdatedAt());
        return vo;
    }

    private SkillProfileListVO toListVO(SkillProfile profile) {
        SkillProfileListVO vo = new SkillProfileListVO();
        vo.setProfileId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setTargetJobId(profile.getTargetJobId());
        vo.setMatchReportId(profile.getMatchReportId());
        vo.setProfileName(profile.getProfileName());
        vo.setOverallLevel(profile.getOverallLevel());
        vo.setOverallScore(profile.getOverallScore());
        vo.setSummary(profile.getSummary());
        vo.setSourceType(profile.getSourceType());
        vo.setSourceBizId(profile.getSourceBizId());
        vo.setStatus(profile.getStatus());
        vo.setErrorMessage(profile.getErrorMessage());
        vo.setGapCount(listGapItems(profile).size());
        vo.setAiCallLogId(profile.getAiCallLogId());
        vo.setCreatedAt(profile.getCreatedAt());
        vo.setUpdatedAt(profile.getUpdatedAt());
        return vo;
    }

    private SkillProfileGenerateVO toGenerateVO(SkillProfile profile) {
        SkillProfileGenerateVO vo = new SkillProfileGenerateVO();
        vo.setProfileId(profile.getId());
        vo.setTargetJobId(profile.getTargetJobId());
        vo.setMatchReportId(profile.getMatchReportId());
        vo.setGapCount(listGapItems(profile).size());
        vo.setStatus(profile.getStatus());
        vo.setErrorMessage(profile.getErrorMessage());
        vo.setAiCallLogId(profile.getAiCallLogId());
        vo.setCreatedAt(profile.getCreatedAt());
        vo.setUpdatedAt(profile.getUpdatedAt());
        return vo;
    }

    private SkillProfileOverviewVO toOverviewVO(SkillProfile profile) {
        List<SkillGapItemVO> gaps = listGapItems(profile);
        JsonNode raw = readJsonOrNull(profile.getRawResultJson());
        SkillProfileOverviewVO vo = new SkillProfileOverviewVO();
        vo.setEmpty(false);
        vo.setProfileId(profile.getId());
        vo.setTargetJobId(profile.getTargetJobId());
        vo.setProfileName(profile.getProfileName());
        vo.setOverallLevel(profile.getOverallLevel());
        vo.setOverallScore(profile.getOverallScore());
        vo.setStatus(profile.getStatus());
        vo.setSummary(profile.getSummary());
        vo.setRadarData(gaps.stream().map(this::toRadarDataItem).toList());
        vo.setTopGaps(gaps.stream()
                .sorted(Comparator.comparing(item -> firstInteger(item.getPriority(), Integer.MAX_VALUE)))
                .limit(5)
                .toList());
        vo.setNextPrioritySkills(raw == null ? objectMapper.createArrayNode() : jsonArrayNode(raw, "nextPrioritySkills"));
        vo.setNextActions(raw == null ? objectMapper.createArrayNode() : jsonArrayNode(raw, "nextActions"));
        vo.setGapCount(gaps.size());
        return vo;
    }

    private SkillProfileOverviewVO emptyOverview(Long targetJobId) {
        SkillProfileOverviewVO vo = new SkillProfileOverviewVO();
        vo.setEmpty(true);
        vo.setTargetJobId(targetJobId);
        vo.setRadarData(List.of());
        vo.setTopGaps(List.of());
        vo.setNextPrioritySkills(objectMapper.createArrayNode());
        vo.setNextActions(objectMapper.createArrayNode());
        vo.setGapCount(0);
        return vo;
    }

    private SkillProfileOverviewVO.RadarDataItemVO toRadarDataItem(SkillGapItemVO gap) {
        SkillProfileOverviewVO.RadarDataItemVO item = new SkillProfileOverviewVO.RadarDataItemVO();
        item.setSkillName(gap.getSkillName());
        item.setCategory(gap.getCategory());
        item.setTargetLevel(gap.getTargetLevel());
        item.setCurrentLevel(gap.getCurrentLevel());
        item.setGapLevel(gap.getGapLevel());
        item.setSeverity(gap.getSeverity());
        return item;
    }

    private InnerSkillProfileVO toInnerProfileVO(SkillProfile profile) {
        TargetJob targetJob = targetJobMapper.selectById(profile.getTargetJobId());
        InnerSkillProfileVO vo = new InnerSkillProfileVO();
        vo.setProfileId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setTargetJobId(profile.getTargetJobId());
        vo.setMatchReportId(profile.getMatchReportId());
        vo.setProfileName(profile.getProfileName());
        vo.setOverallLevel(profile.getOverallLevel());
        vo.setOverallScore(profile.getOverallScore());
        vo.setSummary(profile.getSummary());
        vo.setSourceType(profile.getSourceType());
        vo.setSourceBizId(profile.getSourceBizId());
        vo.setStatus(profile.getStatus());
        vo.setRawResultJson(profile.getRawResultJson());
        vo.setAiCallLogId(profile.getAiCallLogId());
        vo.setErrorMessage(profile.getErrorMessage());
        if (targetJob != null && CommonConstants.NO.equals(targetJob.getDeleted())) {
            vo.setTargetJobTitle(targetJob.getJobTitle());
            vo.setTargetCompanyName(targetJob.getCompanyName());
            vo.setTargetJobLevel(targetJob.getJobLevel());
            vo.setTargetJdSource(targetJob.getJdSource());
        }
        vo.setGapItems(innerGapItems(profile));
        vo.setCreatedAt(profile.getCreatedAt());
        vo.setUpdatedAt(profile.getUpdatedAt());
        return vo;
    }

    private List<InnerSkillGapItemVO> innerGapItems(SkillProfile profile) {
        return gapItemMapper.selectList(new LambdaQueryWrapper<SkillGapItem>()
                        .eq(SkillGapItem::getProfileId, profile.getId())
                        .eq(SkillGapItem::getUserId, profile.getUserId())
                        .eq(SkillGapItem::getDeleted, CommonConstants.NO)
                        .orderByAsc(SkillGapItem::getPriority)
                        .orderByAsc(SkillGapItem::getId))
                .stream()
                .map(this::toInnerGapItemVO)
                .toList();
    }

    private InnerSkillGapItemVO toInnerGapItemVO(SkillGapItem item) {
        InnerSkillGapItemVO vo = new InnerSkillGapItemVO();
        vo.setId(item.getId());
        vo.setProfileId(item.getProfileId());
        vo.setUserId(item.getUserId());
        vo.setTargetJobId(item.getTargetJobId());
        vo.setSkillName(item.getSkillName());
        vo.setCategory(item.getCategory());
        vo.setTargetLevel(item.getTargetLevel());
        vo.setCurrentLevel(item.getCurrentLevel());
        vo.setGapLevel(item.getGapLevel());
        vo.setConfidence(item.getConfidence());
        vo.setSeverity(item.getSeverity());
        vo.setEvidenceSourcesJson(item.getEvidenceSourcesJson());
        vo.setGapDescription(item.getGapDescription());
        vo.setRecommendedActionsJson(item.getRecommendedActionsJson());
        vo.setPriority(item.getPriority());
        vo.setSourceType(item.getSourceType());
        vo.setSourceBizId(item.getSourceBizId());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private List<SkillGapItemVO> listGapItems(SkillProfile profile) {
        return gapItemMapper.selectList(new LambdaQueryWrapper<SkillGapItem>()
                        .eq(SkillGapItem::getProfileId, profile.getId())
                        .eq(SkillGapItem::getUserId, profile.getUserId())
                        .eq(SkillGapItem::getDeleted, CommonConstants.NO)
                        .orderByAsc(SkillGapItem::getPriority)
                        .orderByAsc(SkillGapItem::getId))
                .stream()
                .map(this::toGapItemVO)
                .toList();
    }

    private SkillGapItemVO toGapItemVO(SkillGapItem item) {
        SkillGapItemVO vo = new SkillGapItemVO();
        vo.setId(item.getId());
        vo.setProfileId(item.getProfileId());
        vo.setUserId(item.getUserId());
        vo.setTargetJobId(item.getTargetJobId());
        vo.setSkillName(item.getSkillName());
        vo.setCategory(item.getCategory());
        vo.setTargetLevel(item.getTargetLevel());
        vo.setCurrentLevel(item.getCurrentLevel());
        vo.setGapLevel(item.getGapLevel());
        vo.setConfidence(item.getConfidence());
        vo.setSeverity(item.getSeverity());
        vo.setEvidenceSources(readJsonOrEmptyArray(item.getEvidenceSourcesJson()));
        vo.setGapDescription(item.getGapDescription());
        vo.setRecommendedActions(readJsonOrEmptyArray(item.getRecommendedActionsJson()));
        vo.setPriority(item.getPriority());
        vo.setSourceType(item.getSourceType());
        vo.setSourceBizId(item.getSourceBizId());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private Map<String, Object> resumeSnapshot(Resume resume, List<ResumeProject> projects) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (resume != null) {
            snapshot.put("title", resume.getTitle());
            snapshot.put("realName", resume.getRealName());
            snapshot.put("targetPosition", resume.getTargetPosition());
            snapshot.put("skillStack", resume.getSkillStack());
            snapshot.put("workExperience", resume.getWorkExperience());
            snapshot.put("educationExperience", resume.getEducationExperience());
            snapshot.put("summary", resume.getSummary());
        }
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
        if (analysis == null) {
            return snapshot;
        }
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

    private Map<String, Object> matchReportSnapshot(ResumeJobMatchReport report) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reportId", report.getId());
        snapshot.put("resumeId", report.getResumeId());
        snapshot.put("targetJobId", report.getTargetJobId());
        snapshot.put("overallScore", report.getOverallScore());
        snapshot.put("techStackScore", report.getTechStackScore());
        snapshot.put("projectExperienceScore", report.getProjectExperienceScore());
        snapshot.put("businessFitScore", report.getBusinessFitScore());
        snapshot.put("communicationScore", report.getCommunicationScore());
        snapshot.put("strengths", readJsonOrNull(report.getStrengthsJson()));
        snapshot.put("gaps", readJsonOrNull(report.getGapsJson()));
        snapshot.put("resumeRisks", readJsonOrNull(report.getResumeRisksJson()));
        snapshot.put("optimizationSuggestions", readJsonOrNull(report.getOptimizationSuggestionsJson()));
        snapshot.put("recommendedLearningTopics", readJsonOrNull(report.getRecommendedLearningTopicsJson()));
        snapshot.put("recommendedInterviewTopics", readJsonOrNull(report.getRecommendedInterviewTopicsJson()));
        snapshot.put("summary", report.getSummary());
        return snapshot;
    }

    private Map<String, Object> matchDetailSnapshot(ResumeJobMatchDetail detail) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("dimension", detail.getDimension());
        snapshot.put("skillName", detail.getSkillName());
        snapshot.put("matchLevel", detail.getMatchLevel());
        snapshot.put("score", detail.getScore());
        snapshot.put("evidence", detail.getEvidence());
        snapshot.put("gapDescription", detail.getGapDescription());
        snapshot.put("suggestion", detail.getSuggestion());
        return snapshot;
    }

    private JsonNode parseResultJson(String resultJson) {
        JsonNode root = readJsonOrNull(resultJson);
        if (root == null || !root.isObject()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像结果格式异常，请稍后重试");
        }
        JsonNode gaps = root.path("skillGaps");
        if (!gaps.isArray() || gaps.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Skill profile result must contain skillGaps");
        }
        return root;
    }

    private boolean hasEffectiveGaps(JsonNode gaps) {
        if (gaps == null || !gaps.isArray() || gaps.isEmpty()) {
            return false;
        }
        for (JsonNode gap : gaps) {
            if (StringUtils.hasText(textValue(gap, "skillName"))
                    || StringUtils.hasText(textValue(gap, "description"))
                    || StringUtils.hasText(textValue(gap, "gapDescription"))) {
                return true;
            }
        }
        return false;
    }

    private JsonNode readJsonOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "已保存的技能画像结果格式异常");
        }
    }

    private JsonNode readJsonSafely(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode readJsonOrEmptyArray(String raw) {
        JsonNode json = readJsonOrNull(raw);
        return json == null ? objectMapper.createArrayNode() : json;
    }

    private JsonNode jsonArrayNode(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        if (value.isArray()) {
            return value;
        }
        return objectMapper.createArrayNode();
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

    private BigDecimal decimalValue(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        return value.isNumber() ? BigDecimal.valueOf(value.asDouble()) : null;
    }

    private String textValue(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private Integer gapLevel(SkillGapItem item) {
        if (item.getTargetLevel() == null || item.getCurrentLevel() == null) {
            return null;
        }
        return Math.max(0, item.getTargetLevel() - item.getCurrentLevel());
    }

    private String severityFromGapLevel(Integer gapLevel) {
        if (gapLevel == null) {
            return "MEDIUM";
        }
        if (gapLevel >= 3) {
            return "HIGH";
        }
        if (gapLevel <= 1) {
            return "LOW";
        }
        return "MEDIUM";
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

    private String truncateErrorMessage(String message) {
        String value = StringUtils.hasText(message) ? message : "技能画像分析失败";
        return value.length() <= MAX_ERROR_MESSAGE_LENGTH ? value : value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
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

    private Integer firstInteger(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record SkillProfileContext(Resume resume, List<ResumeProject> projects,
                                       ResumeAnalysisRecord resumeAnalysis, TargetJob targetJob,
                                       JobDescriptionAnalysis jdAnalysis, ResumeJobMatchReport report,
                                       List<ResumeJobMatchDetail> details) {
    }
}
