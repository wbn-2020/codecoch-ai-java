package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
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
import com.codecoachai.resume.service.TargetJobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
        return targetJobMapper.selectList(wrapper).stream()
                .map(job -> toTargetJobVO(job, latestAnalysis(job.getId(), userId)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetJobVO createTargetJob(TargetJobSaveDTO dto) {
        Long userId = requireCurrentUserId();
        TargetJob job = new TargetJob();
        job.setUserId(userId);
        applyTargetJob(job, dto);
        job.setCurrentFlag(CommonConstants.NO);
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
        JobDescriptionParseDTO request = dto == null ? new JobDescriptionParseDTO() : dto;
        TargetJob job = getOwnedTargetJob(id, userId);
        if (!StringUtils.hasText(job.getJdText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "jdText is required before parsing");
        }

        JobDescriptionAnalysis existing = latestAnalysis(id, userId);
        if (existing != null
                && JobDescriptionParseStatus.PARSED.getCode().equals(existing.getParseStatus())
                && !Boolean.TRUE.equals(request.getForceRefresh())) {
            return toAnalysisVO(existing);
        }
        if (JobDescriptionParseStatus.PARSING.getCode().equals(job.getParseStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "JD is parsing");
        }

        JobDescriptionAnalysis parsing = transactionTemplate.execute(status -> markParsing(job, existing, request));
        try {
            ParseJobDescriptionVO aiResponse = FeignResultUtils.unwrap(aiFeignClient.parseJobDescription(toAiRequest(job, request)));
            JsonNode resultJson = parseResultJson(aiResponse == null ? null : aiResponse.getResultJson());
            JobDescriptionAnalysis parsed = transactionTemplate.execute(status ->
                    markParsed(job.getId(), userId, parsing.getId(), resultJson,
                            aiResponse == null ? null : aiResponse.getAiCallLogId()));
            return toAnalysisVO(parsed);
        } catch (RuntimeException ex) {
            JobDescriptionAnalysis failed = transactionTemplate.execute(status ->
                    markFailed(job.getId(), userId, parsing.getId(), ex));
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

    private JobDescriptionAnalysis markParsing(TargetJob job, JobDescriptionAnalysis existing,
                                               JobDescriptionParseDTO request) {
        targetJobMapper.update(null, new LambdaUpdateWrapper<TargetJob>()
                .set(TargetJob::getParseStatus, JobDescriptionParseStatus.PARSING.getCode())
                .set(TargetJob::getParseErrorMessage, null)
                .eq(TargetJob::getId, job.getId())
                .eq(TargetJob::getUserId, job.getUserId())
                .eq(TargetJob::getDeleted, CommonConstants.NO));
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JD analysis record missing");
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
        String message = truncate(firstText(ex.getMessage(), "JD parse failed"), MAX_ERROR_MESSAGE_LENGTH);
        JobDescriptionAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null || !userId.equals(analysis.getUserId())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JD analysis record missing");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "target job id is required");
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

    private JobDescriptionAnalysis latestAnalysis(Long targetJobId, Long userId) {
        return analysisMapper.selectOne(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                .eq(JobDescriptionAnalysis::getUserId, userId)
                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                .last("limit 1"));
    }

    private void applyTargetJob(TargetJob job, TargetJobSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "request body is required");
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
        vo.setRawResult(readJsonOrNull(analysis.getRawResultJson()));
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI JD parse response must be a JSON object");
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Stored JD analysis JSON is invalid");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "user id is required");
        }
    }

    private boolean sameText(String left, String right) {
        return firstText(left, "").equals(firstText(right, ""));
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
