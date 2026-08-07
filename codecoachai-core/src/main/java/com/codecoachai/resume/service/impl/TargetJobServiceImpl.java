package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobDescriptionParseDTO;
import com.codecoachai.resume.domain.dto.TargetJobQueryDTO;
import com.codecoachai.resume.domain.dto.TargetJobSaveDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.vo.JobDescriptionAnalysisVO;
import com.codecoachai.resume.domain.vo.TargetJobVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.dto.ParseJobDescriptionDTO;
import com.codecoachai.resume.feign.vo.ParseJobDescriptionVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.JobTargetParseMqDispatcher;
import com.codecoachai.resume.service.TargetJobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TargetJobServiceImpl implements TargetJobService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper analysisMapper;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DistributedLockHelper distributedLockHelper;
    private final Optional<JobTargetParseMqDispatcher> jobTargetParseMqDispatcher;

    @Override
    public List<TargetJobVO> listTargetJobs(TargetJobQueryDTO query) {
        Long userId = requireCurrentUserId();
        LambdaQueryWrapper<TargetJob> wrapper = new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId)
                .orderByDesc(TargetJob::getCurrentFlag)
                .orderByDesc(TargetJob::getUpdatedAt);
        if (query != null) {
            if (StringUtils.hasText(query.getKeyword())) {
                String keyword = query.getKeyword().trim();
                wrapper.and(item -> item.like(TargetJob::getJobTitle, keyword)
                        .or()
                        .like(TargetJob::getCompanyName, keyword));
            }
            if (query.getStatus() != null) {
                wrapper.eq(TargetJob::getStatus, query.getStatus());
            }
            if (query.getCurrent() != null) {
                wrapper.eq(TargetJob::getCurrentFlag, Boolean.TRUE.equals(query.getCurrent())
                        ? CommonConstants.YES : CommonConstants.NO);
            }
        }
        List<TargetJob> jobs = targetJobMapper.selectList(wrapper);
        Map<Long, JobDescriptionAnalysis> latestAnalysisByTargetJobId = latestAnalysisByTargetJobId(jobs, userId);
        return jobs.stream()
                .map(job -> toTargetJobVO(job, latestAnalysisByTargetJobId.get(job.getId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetJobVO createTargetJob(TargetJobSaveDTO dto) {
        Long userId = requireCurrentUserId();
        return distributedLockHelper.tryLockAndCall("lock:target-job:current:" + userId, 3, 10,
                () -> createTargetJobInLock(userId, dto),
                () -> {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Target job create is busy");
                });
    }

    private TargetJobVO createTargetJobInLock(Long userId, TargetJobSaveDTO dto) {
        TargetJob job = new TargetJob();
        job.setUserId(userId);
        applyTargetJob(job, dto);
        job.setCurrentFlag(hasCurrentTargetJob(userId) ? CommonConstants.NO : CommonConstants.YES);
        job.setStatus(CommonConstants.YES);
        job.setParseStatus(JobDescriptionParseStatus.NOT_PARSED.getCode());
        targetJobMapper.insert(job);
        return toTargetJobVO(job, null);
    }

    @Override
    public TargetJobVO getTargetJob(Long id) {
        Long userId = requireCurrentUserId();
        return getTargetJobForUser(id, userId);
    }

    @Override
    public TargetJobVO getTargetJobForUser(Long id, Long userId) {
        requireUserId(userId);
        TargetJob job = getOwnedTargetJob(id, userId);
        return toTargetJobVO(job, latestAnalysis(job.getId(), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetJobVO updateTargetJob(Long id, TargetJobSaveDTO dto) {
        Long userId = requireCurrentUserId();
        TargetJob job = getOwnedTargetJob(id, userId);
        String oldJdText = job.getJdText();
        applyTargetJob(job, dto);
        if (!sameText(oldJdText, job.getJdText())) {
            job.setParseStatus(JobDescriptionParseStatus.NOT_PARSED.getCode());
            job.setParseErrorMessage(null);
        }
        targetJobMapper.updateById(job);
        return toTargetJobVO(job, latestAnalysis(job.getId(), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTargetJob(Long id) {
        Long userId = requireCurrentUserId();
        TargetJob job = getOwnedTargetJob(id, userId);
        analysisMapper.delete(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getTargetJobId, job.getId())
                .eq(JobDescriptionAnalysis::getUserId, userId));
        targetJobMapper.deleteById(job.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetJobVO setCurrent(Long id) {
        Long userId = requireCurrentUserId();
        return distributedLockHelper.tryLockAndCall("lock:target-job:current:" + userId, 3, 10,
                () -> setCurrentInTransaction(id, userId),
                () -> {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Target job current switch is busy");
                });
    }

    private TargetJobVO setCurrentInTransaction(Long id, Long userId) {
        return transactionTemplate.execute(status -> {
            TargetJob job = getOwnedTargetJob(id, userId);
            targetJobMapper.update(null, new LambdaUpdateWrapper<TargetJob>()
                    .set(TargetJob::getCurrentFlag, CommonConstants.NO)
                    .eq(TargetJob::getUserId, userId)
                    .eq(TargetJob::getDeleted, CommonConstants.NO)
                    .ne(TargetJob::getId, id));
            job.setCurrentFlag(CommonConstants.YES);
            targetJobMapper.updateById(job);
            return toTargetJobVO(job, latestAnalysis(job.getId(), userId));
        });
    }

    @Override
    public TargetJobVO getCurrent() {
        Long userId = requireCurrentUserId();
        return getCurrentForUser(userId);
    }

    @Override
    public TargetJobVO getCurrentForUser(Long userId) {
        requireUserId(userId);
        TargetJob job = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getCurrentFlag, CommonConstants.YES)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .orderByDesc(TargetJob::getUpdatedAt)
                .last("limit 1"));
        return job == null ? null : toTargetJobVO(job, latestAnalysis(job.getId(), userId));
    }

    @Override
    public JobDescriptionAnalysisVO parseJobDescription(Long id, JobDescriptionParseDTO dto) {
        Long userId = requireCurrentUserId();
        return parseJobDescriptionForUser(id, userId, dto);
    }

    @Override
    public JobDescriptionAnalysisVO submitJobDescriptionParse(Long id, JobDescriptionParseDTO dto) {
        Long userId = requireCurrentUserId();
        JobDescriptionParseDTO request = dto == null ? new JobDescriptionParseDTO() : dto;
        TargetJob job = getOwnedTargetJob(id, userId);
        validateParseable(job);

        JobDescriptionAnalysis existing = latestAnalysis(id, userId);
        if (existing != null
                && JobDescriptionParseStatus.PARSED.getCode().equals(existing.getParseStatus())
                && !Boolean.TRUE.equals(request.getForceRefresh())) {
            return toAnalysisVO(existing);
        }
        if (JobDescriptionParseStatus.PARSING.getCode().equals(job.getParseStatus())) {
            JobDescriptionAnalysisVO parsing = existing == null ? toParsingHintVO(job, userId) : toAnalysisVO(existing);
            return attachSyntheticTaskHint(parsing, id, userId);
        }

        JobDescriptionAnalysis parsing = transactionTemplate.execute(status -> markParsing(job, existing, request));
        MqDispatchReceipt receipt = dispatchParse(job, request);
        if (receipt != null) {
            return withAsyncReceipt(toAnalysisVO(parsing), receipt);
        }
        return parseMarkedJob(job, userId, parsing.getId(), request);
    }

    @Override
    public JobDescriptionAnalysisVO parseJobDescriptionForUser(Long id, Long userId, JobDescriptionParseDTO dto) {
        requireUserId(userId);
        JobDescriptionParseDTO request = dto == null ? new JobDescriptionParseDTO() : dto;
        TargetJob job = getOwnedTargetJob(id, userId);
        validateParseable(job);

        JobDescriptionAnalysis existing = latestAnalysis(id, userId);
        if (existing != null
                && JobDescriptionParseStatus.PARSED.getCode().equals(existing.getParseStatus())
                && !Boolean.TRUE.equals(request.getForceRefresh())) {
            return toAnalysisVO(existing);
        }
        if (JobDescriptionParseStatus.PARSING.getCode().equals(job.getParseStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位分析正在生成中，请稍后查看。");
        }

        JobDescriptionAnalysis parsing = transactionTemplate.execute(status -> markParsing(job, existing, request));
        return parseMarkedJob(job, userId, parsing.getId(), request);
    }

    @Override
    public JobDescriptionAnalysisVO executeJobDescriptionParseForUser(Long id, Long userId, JobDescriptionParseDTO dto) {
        requireUserId(userId);
        JobDescriptionParseDTO request = dto == null ? new JobDescriptionParseDTO() : dto;
        TargetJob job = getOwnedTargetJob(id, userId);
        validateParseable(job);

        JobDescriptionAnalysis existing = latestAnalysis(id, userId);
        if (existing != null
                && JobDescriptionParseStatus.PARSED.getCode().equals(existing.getParseStatus())
                && !Boolean.TRUE.equals(request.getForceRefresh())) {
            return toAnalysisVO(existing);
        }

        JobDescriptionAnalysis parsing = existing != null
                && JobDescriptionParseStatus.PARSING.getCode().equals(existing.getParseStatus())
                ? existing
                : transactionTemplate.execute(status -> markParsing(job, existing, request));
        return parseMarkedJob(job, userId, parsing.getId(), request);
    }

    private JobDescriptionAnalysisVO parseMarkedJob(TargetJob job, Long userId, Long analysisId,
                                                    JobDescriptionParseDTO request) {
        try {
            ParseJobDescriptionVO aiResponse = FeignResultUtils.unwrap(aiFeignClient.parseJobDescription(toAiRequest(job, request)));
            JsonNode resultJson = parseResultJson(aiResponse == null ? null : aiResponse.getResultJson());
            JobDescriptionAnalysis parsed = transactionTemplate.execute(status ->
                    markParsed(job.getId(), userId, analysisId, resultJson,
                            aiResponse == null ? null : aiResponse.getAiCallLogId()));
            return toAnalysisVO(parsed);
        } catch (RuntimeException ex) {
            JobDescriptionAnalysis failed = transactionTemplate.execute(status ->
                    markFailed(job.getId(), userId, analysisId, ex));
            return toAnalysisVO(failed);
        }
    }

    @Override
    public JobDescriptionAnalysisVO getAnalysis(Long id) {
        Long userId = requireCurrentUserId();
        return getAnalysisForUser(id, userId);
    }

    @Override
    public JobDescriptionAnalysisVO getAnalysisForUser(Long id, Long userId) {
        requireUserId(userId);
        getOwnedTargetJob(id, userId);
        JobDescriptionAnalysis analysis = latestAnalysis(id, userId);
        return analysis == null ? null : toAnalysisVO(analysis);
    }

    private void validateParseable(TargetJob job) {
        if (!StringUtils.hasText(job.getJdText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位描述内容不能为空，请先填写后再解析");
        }
    }

    private MqDispatchReceipt dispatchParse(TargetJob job, JobDescriptionParseDTO request) {
        return job == null ? null : jobTargetParseMqDispatcher
                .map(dispatcher -> dispatcher.dispatchParseWithReceipt(
                        job.getId(), job.getUserId(), request.getForceRefresh(), request.getUserTargetDirection()))
                .orElse(null);
    }

    private JobDescriptionAnalysisVO withAsyncReceipt(JobDescriptionAnalysisVO vo, MqDispatchReceipt receipt) {
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

    private JobDescriptionAnalysisVO attachSyntheticTaskHint(JobDescriptionAnalysisVO vo, Long targetJobId, Long userId) {
        if (vo == null) {
            return null;
        }
        vo.setAsyncBizType(JobTargetParseMqDispatcher.BIZ_TYPE);
        vo.setAsyncBizId(targetJobId == null ? null : String.valueOf(targetJobId));
        if (vo.getUserId() == null) {
            vo.setUserId(userId);
        }
        return vo;
    }

    private JobDescriptionAnalysisVO toParsingHintVO(TargetJob job, Long userId) {
        JobDescriptionAnalysisVO vo = new JobDescriptionAnalysisVO();
        vo.setTargetJobId(job.getId());
        vo.setUserId(userId);
        vo.setJobTitle(job.getJobTitle());
        vo.setCompanyName(job.getCompanyName());
        vo.setJobLevel(job.getJobLevel());
        vo.setParseStatus(JobDescriptionParseStatus.PARSING.getCode());
        vo.setParseErrorMessage(job.getParseErrorMessage());
        vo.setUpdatedAt(job.getUpdatedAt());
        return vo;
    }

    private JobDescriptionAnalysis markParsing(TargetJob job, JobDescriptionAnalysis existing,
                                               JobDescriptionParseDTO request) {
        int affectedRows = targetJobMapper.update(null, new LambdaUpdateWrapper<TargetJob>()
                .set(TargetJob::getParseStatus, JobDescriptionParseStatus.PARSING.getCode())
                .set(TargetJob::getParseErrorMessage, null)
                .eq(TargetJob::getId, job.getId())
                .eq(TargetJob::getUserId, job.getUserId())
                .ne(TargetJob::getParseStatus, JobDescriptionParseStatus.PARSING.getCode())
                .eq(TargetJob::getDeleted, CommonConstants.NO));
        if (affectedRows <= 0) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Job description parse is already running");
        }
        JobDescriptionAnalysis analysis = existing == null || Boolean.TRUE.equals(request.getForceRefresh())
                ? new JobDescriptionAnalysis() : existing;
        analysis.setTargetJobId(job.getId());
        analysis.setUserId(job.getUserId());
        analysis.setJobTitle(job.getJobTitle());
        analysis.setCompanyName(job.getCompanyName());
        analysis.setJobLevel(job.getJobLevel());
        analysis.setParseStatus(JobDescriptionParseStatus.PARSING.getCode());
        analysis.setParseErrorMessage(null);
        if (analysis.getId() == null) {
            analysisMapper.insert(analysis);
        } else {
            analysisMapper.updateById(analysis);
        }
        return analysis;
    }

    private JobDescriptionAnalysis markParsed(Long targetJobId, Long userId, Long analysisId,
                                              JsonNode resultJson, Long aiCallLogId) {
        JobDescriptionAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null || !userId.equals(analysis.getUserId())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "岗位分析记录不存在或已失效。");
        }
        analysis.setResponsibilitiesJson(jsonArrayText(resultJson, "responsibilities"));
        analysis.setRequiredSkillsJson(jsonArrayText(resultJson, "requiredSkills"));
        analysis.setBonusSkillsJson(jsonArrayText(resultJson, "bonusSkills"));
        analysis.setTechKeywordsJson(jsonArrayText(resultJson, "techStackKeywords", "techKeywords"));
        analysis.setBusinessKeywordsJson(jsonArrayText(resultJson, "businessKeywords"));
        analysis.setExperienceRequirement(textValue(resultJson, "experienceRequirement"));
        analysis.setProjectExperienceRequirement(textValue(resultJson, "projectExperienceRequirement"));
        analysis.setInterviewFocusJson(jsonArrayText(resultJson, "interviewFocusPoints", "interviewFocus"));
        analysis.setSkillWeightsJson(jsonValueText(resultJson, "skillWeights"));
        analysis.setSummary(textValue(resultJson, "summary"));
        analysis.setRawResultJson(resultJson.toString());
        analysis.setAiCallLogId(aiCallLogId);
        analysis.setParseStatus(JobDescriptionParseStatus.PARSED.getCode());
        analysis.setParseErrorMessage(null);
        analysisMapper.updateById(analysis);

        targetJobMapper.update(null, new LambdaUpdateWrapper<TargetJob>()
                .set(TargetJob::getParseStatus, JobDescriptionParseStatus.PARSED.getCode())
                .set(TargetJob::getParseErrorMessage, null)
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO));
        return analysis;
    }

    private JobDescriptionAnalysis markFailed(Long targetJobId, Long userId, Long analysisId, RuntimeException ex) {
        String message = truncate(friendlyJobDescriptionError(ex), MAX_ERROR_MESSAGE_LENGTH);
        JobDescriptionAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null || !userId.equals(analysis.getUserId())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "岗位分析记录不存在或已失效。");
        }
        analysis.setParseStatus(JobDescriptionParseStatus.FAILED.getCode());
        analysis.setParseErrorMessage(message);
        analysisMapper.updateById(analysis);
        targetJobMapper.update(null, new LambdaUpdateWrapper<TargetJob>()
                .set(TargetJob::getParseStatus, JobDescriptionParseStatus.FAILED.getCode())
                .set(TargetJob::getParseErrorMessage, message)
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO));
        return analysis;
    }

    private ParseJobDescriptionDTO toAiRequest(TargetJob job, JobDescriptionParseDTO dto) {
        ParseJobDescriptionDTO request = new ParseJobDescriptionDTO();
        request.setTargetJobId(job.getId());
        request.setUserId(job.getUserId());
        request.setJobTitle(job.getJobTitle());
        request.setCompanyName(job.getCompanyName());
        request.setJobLevel(job.getJobLevel());
        request.setJdText(job.getJdText());
        request.setJdSource(job.getJdSource());
        request.setUserTargetDirection(dto.getUserTargetDirection());
        return request;
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

    private boolean hasCurrentTargetJob(Long userId) {
        return targetJobMapper.selectCount(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getCurrentFlag, CommonConstants.YES)
                .eq(TargetJob::getDeleted, CommonConstants.NO)) > 0;
    }

    private JobDescriptionAnalysis latestAnalysis(Long targetJobId, Long userId) {
        return analysisMapper.selectOne(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                .eq(JobDescriptionAnalysis::getUserId, userId)
                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                .last("limit 1"));
    }

    private Map<Long, JobDescriptionAnalysis> latestAnalysisByTargetJobId(List<TargetJob> jobs, Long userId) {
        List<Long> targetJobIds = jobs.stream()
                .map(TargetJob::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (targetJobIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<JobDescriptionAnalysis> analyses = analysisMapper.selectList(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getUserId, userId)
                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                .in(JobDescriptionAnalysis::getTargetJobId, targetJobIds)
                .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                .orderByDesc(JobDescriptionAnalysis::getId));
        if (analyses == null || analyses.isEmpty()) {
            return Collections.emptyMap();
        }
        return analyses.stream()
                .filter(analysis -> analysis != null && analysis.getTargetJobId() != null)
                .sorted(this::compareLatestAnalysisFirst)
                .collect(Collectors.toMap(
                        JobDescriptionAnalysis::getTargetJobId,
                        analysis -> analysis,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
    }

    private int compareLatestAnalysisFirst(JobDescriptionAnalysis left, JobDescriptionAnalysis right) {
        Comparator<JobDescriptionAnalysis> latestFirst = Comparator
                .comparing(JobDescriptionAnalysis::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(JobDescriptionAnalysis::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        return latestFirst.compare(left, right);
    }

    private void applyTargetJob(TargetJob job, TargetJobSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        job.setJobTitle(dto.getJobTitle());
        job.setCompanyName(dto.getCompanyName());
        job.setJobLevel(dto.getJobLevel());
        job.setJdText(dto.getJdText());
        job.setJdSource(dto.getJdSource());
    }

    private TargetJobVO toTargetJobVO(TargetJob job, JobDescriptionAnalysis analysis) {
        TargetJobVO vo = new TargetJobVO();
        vo.setId(job.getId());
        vo.setUserId(job.getUserId());
        vo.setJobTitle(job.getJobTitle());
        vo.setCompanyName(job.getCompanyName());
        vo.setJobLevel(job.getJobLevel());
        vo.setJdText(job.getJdText());
        vo.setJdSource(job.getJdSource());
        vo.setCurrentFlag(job.getCurrentFlag());
        vo.setStatus(job.getStatus());
        vo.setParseStatus(job.getParseStatus());
        vo.setParseErrorMessage(job.getParseErrorMessage());
        vo.setCreatedAt(job.getCreatedAt());
        vo.setUpdatedAt(job.getUpdatedAt());
        if (analysis != null) {
            vo.setAnalysisSummary(analysis.getSummary());
            vo.setRequiredSkills(readJsonOrNull(analysis.getRequiredSkillsJson()));
            vo.setInterviewFocusPoints(readJsonOrNull(analysis.getInterviewFocusJson()));
        }
        return vo;
    }

    private JobDescriptionAnalysisVO toAnalysisVO(JobDescriptionAnalysis analysis) {
        JobDescriptionAnalysisVO vo = new JobDescriptionAnalysisVO();
        vo.setId(analysis.getId());
        vo.setTargetJobId(analysis.getTargetJobId());
        vo.setUserId(analysis.getUserId());
        vo.setJobTitle(analysis.getJobTitle());
        vo.setCompanyName(analysis.getCompanyName());
        vo.setJobLevel(analysis.getJobLevel());
        vo.setResponsibilities(readJsonOrNull(analysis.getResponsibilitiesJson()));
        vo.setRequiredSkills(readJsonOrNull(analysis.getRequiredSkillsJson()));
        vo.setBonusSkills(readJsonOrNull(analysis.getBonusSkillsJson()));
        vo.setTechStackKeywords(readJsonOrNull(analysis.getTechKeywordsJson()));
        vo.setBusinessKeywords(readJsonOrNull(analysis.getBusinessKeywordsJson()));
        vo.setExperienceRequirement(analysis.getExperienceRequirement());
        vo.setProjectExperienceRequirement(analysis.getProjectExperienceRequirement());
        vo.setInterviewFocusPoints(readJsonOrNull(analysis.getInterviewFocusJson()));
        vo.setSkillWeights(readJsonOrNull(analysis.getSkillWeightsJson()));
        vo.setSummary(analysis.getSummary());
        vo.setAiCallLogId(analysis.getAiCallLogId());
        vo.setParseStatus(analysis.getParseStatus());
        vo.setParseErrorMessage(analysis.getParseErrorMessage());
        vo.setCreatedAt(analysis.getCreatedAt());
        vo.setUpdatedAt(analysis.getUpdatedAt());
        return vo;
    }

    private JsonNode parseResultJson(String raw) {
        JsonNode json = readJsonOrNull(raw);
        if (json == null || !json.isObject()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "岗位分析结果暂时无法整理，请重新生成。");
        }
        return json;
    }

    private JsonNode readJsonOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "岗位分析结果暂时不可用，请重新生成。");
        }
    }

    private String jsonArrayText(JsonNode json, String... fieldNames) {
        JsonNode value = jsonValue(json, fieldNames);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "[]";
        }
        return value.isArray() ? value.toString() : objectMapper.createArrayNode().add(value.asText()).toString();
    }

    private String jsonValueText(JsonNode json, String... fieldNames) {
        JsonNode value = jsonValue(json, fieldNames);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.toString();
    }

    private String textValue(JsonNode json, String fieldName) {
        JsonNode value = json.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private JsonNode jsonValue(JsonNode json, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = json.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息缺失，请重新登录后再试");
        }
    }

    private boolean sameText(String left, String right) {
        return firstText(left, "").equals(firstText(right, ""));
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String friendlyJobDescriptionError(RuntimeException ex) {
        String message = firstText(ex == null ? null : ex.getMessage(), "岗位分析生成失败，请稍后重试。");
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("json")
                || lower.contains("parse")
                || lower.contains("response")
                || lower.contains("object")
                || lower.contains("field")
                || lower.contains("jd")) {
            return "岗位分析结果暂时无法整理，请重新生成或补充岗位描述后再试。";
        }
        return message.replace("JD", "岗位描述");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
