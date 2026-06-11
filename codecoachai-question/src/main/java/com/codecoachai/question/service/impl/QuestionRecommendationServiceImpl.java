package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromGapDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromMatchReportDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromStudyPlanDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationQueryDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionRecommendationBatch;
import com.codecoachai.question.domain.entity.QuestionRecommendationItem;
import com.codecoachai.question.domain.enums.QuestionRecommendationBatchStatus;
import com.codecoachai.question.domain.enums.QuestionRecommendationMatchStatus;
import com.codecoachai.question.domain.enums.QuestionRecommendationPracticeStatus;
import com.codecoachai.question.domain.enums.QuestionRecommendationSourceType;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchDetailVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchListVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationItemVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationSourceTypeVO;
import com.codecoachai.question.feign.AiQuestionRecommendationFeignClient;
import com.codecoachai.question.feign.ResumeProfileFeignClient;
import com.codecoachai.question.feign.StudyPlanFeignClient;
import com.codecoachai.question.feign.dto.GenerateQuestionRecommendationDTO;
import com.codecoachai.question.feign.vo.GenerateQuestionRecommendationVO;
import com.codecoachai.question.feign.vo.InnerSkillGapItemVO;
import com.codecoachai.question.feign.vo.InnerSkillProfileVO;
import com.codecoachai.question.feign.vo.InnerStudyPlanSkillRelationVO;
import com.codecoachai.question.feign.vo.InnerStudyPlanVO;
import com.codecoachai.question.feign.vo.InnerStudyTaskVO;
import com.codecoachai.question.feign.vo.QuestionRecommendationDraftItemVO;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRecommendationBatchMapper;
import com.codecoachai.question.mapper.QuestionRecommendationItemMapper;
import com.codecoachai.question.mq.QuestionMqDispatcher;
import com.codecoachai.question.service.QuestionRecommendationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionRecommendationServiceImpl implements QuestionRecommendationService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final long DEFAULT_PAGE_NO = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String DEFAULT_STRATEGY = "GAP_PRIORITY";
    private static final String DEFAULT_DIFFICULTY = "MEDIUM";
    private static final String TRUST_VERIFIED = "VERIFIED";
    private static final String TRUST_PARTIAL = "PARTIAL";
    private static final String TRUST_FALLBACK = "FALLBACK";

    private final QuestionRecommendationBatchMapper batchMapper;
    private final QuestionRecommendationItemMapper itemMapper;
    private final QuestionMapper questionMapper;
    private final ResumeProfileFeignClient resumeProfileFeignClient;
    private final StudyPlanFeignClient studyPlanFeignClient;
    private final AiQuestionRecommendationFeignClient aiRecommendationFeignClient;
    private final ObjectMapper objectMapper;
    private final Optional<QuestionMqDispatcher> questionMqDispatcher;

    @Override
    public QuestionRecommendationGenerateVO generateFromGap(QuestionRecommendationGenerateFromGapDTO dto) {
        Long userId = requireCurrentUserId();
        return generate(buildGapRequest(dto, userId), userId);
    }

    @Override
    public QuestionRecommendationGenerateVO generateFromMatchReport(
            QuestionRecommendationGenerateFromMatchReportDTO dto) {
        Long userId = requireCurrentUserId();
        return generate(buildMatchReportRequest(dto, userId), userId);
    }

    @Override
    public QuestionRecommendationGenerateVO generateFromStudyPlan(QuestionRecommendationGenerateFromStudyPlanDTO dto) {
        Long userId = requireCurrentUserId();
        return generate(buildStudyPlanRequest(dto, userId), userId);
    }

    @Override
    public QuestionRecommendationGenerateVO submitFromGap(QuestionRecommendationGenerateFromGapDTO dto) {
        Long userId = requireCurrentUserId();
        return submit(buildGapRequest(dto, userId), userId);
    }

    @Override
    public QuestionRecommendationGenerateVO submitFromMatchReport(QuestionRecommendationGenerateFromMatchReportDTO dto) {
        Long userId = requireCurrentUserId();
        return submit(buildMatchReportRequest(dto, userId), userId);
    }

    @Override
    public QuestionRecommendationGenerateVO submitFromStudyPlan(QuestionRecommendationGenerateFromStudyPlanDTO dto) {
        Long userId = requireCurrentUserId();
        return submit(buildStudyPlanRequest(dto, userId), userId);
    }

    @Override
    public QuestionRecommendationGenerateVO executeBatch(Long batchId, Long userId) {
        requireUserId(userId);
        QuestionRecommendationBatch batch = batchMapper.selectOne(
                new LambdaQueryWrapper<QuestionRecommendationBatch>()
                        .eq(QuestionRecommendationBatch::getId, batchId)
                        .eq(QuestionRecommendationBatch::getUserId, userId)
                        .eq(QuestionRecommendationBatch::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "推荐题记录不存在或无权访问");
        }
        assertTrustedBatchEvidence(batch);
        return executeBatch(batch, false);
    }

    private RecommendationRequest buildGapRequest(QuestionRecommendationGenerateFromGapDTO dto, Long userId) {
        if (dto == null || dto.getSkillProfileId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择能力画像后再生成推荐题");
        }
        InnerSkillProfileVO profile = loadOwnedProfile(dto.getSkillProfileId(), userId);
        List<InnerSkillGapItemVO> gaps = resolveSelectedGaps(profile, dto.getGapItemIds());
        return new RecommendationRequest(
                QuestionRecommendationSourceType.JD_GAP,
                profile.getProfileId(),
                profile,
                gaps,
                null,
                normalizeQuestionCount(dto.getQuestionCount()),
                normalizeDifficulty(dto.getDifficultyPreference()),
                normalizeStrategy(dto.getStrategy()));
    }

    private RecommendationRequest buildMatchReportRequest(QuestionRecommendationGenerateFromMatchReportDTO dto,
                                                          Long userId) {
        if (dto == null || dto.getMatchReportId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择匹配报告后再生成推荐题");
        }
        InnerSkillProfileVO profile = FeignResultUtils.unwrap(
                resumeProfileFeignClient.getSuccessSkillProfileByMatchReport(dto.getMatchReportId()));
        validateOwnedProfile(profile, userId);
        List<InnerSkillGapItemVO> gaps = resolveSelectedGaps(profile, dto.getGapItemIds());
        return new RecommendationRequest(
                QuestionRecommendationSourceType.RESUME_JOB_MATCH,
                dto.getMatchReportId(),
                profile,
                gaps,
                null,
                normalizeQuestionCount(dto.getQuestionCount()),
                normalizeDifficulty(dto.getDifficultyPreference()),
                normalizeStrategy(dto.getStrategy()));
    }

    private RecommendationRequest buildStudyPlanRequest(QuestionRecommendationGenerateFromStudyPlanDTO dto,
                                                        Long userId) {
        if (dto == null || dto.getStudyPlanId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择学习计划后再生成推荐题");
        }
        InnerStudyPlanVO plan = FeignResultUtils.unwrap(studyPlanFeignClient.getStudyPlan(dto.getStudyPlanId()));
        validateOwnedPlan(plan, userId);
        if (plan.getSkillProfileId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前学习计划缺少能力画像依据，请先生成能力画像");
        }
        InnerSkillProfileVO profile = loadOwnedProfile(plan.getSkillProfileId(), userId);
        List<Long> requestedGapIds = dto.getGapItemIds();
        if (requestedGapIds == null || requestedGapIds.isEmpty()) {
            requestedGapIds = plan.getSkillRelations() == null ? List.of() : plan.getSkillRelations().stream()
                    .map(InnerStudyPlanSkillRelationVO::getSkillGapItemId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        List<InnerSkillGapItemVO> gaps = resolveSelectedGaps(profile, requestedGapIds);
        return new RecommendationRequest(
                QuestionRecommendationSourceType.STUDY_PLAN,
                plan.getPlanId(),
                profile,
                gaps,
                plan,
                normalizeQuestionCount(dto.getQuestionCount()),
                normalizeDifficulty(dto.getDifficultyPreference()),
                normalizeStrategy(dto.getStrategy()));
    }

    @Override
    public List<QuestionRecommendationSourceTypeVO> sourceTypes() {
        return java.util.Arrays.stream(QuestionRecommendationSourceType.values())
                .map(type -> {
                    QuestionRecommendationSourceTypeVO vo = new QuestionRecommendationSourceTypeVO();
                    vo.setCode(type.getCode());
                    vo.setDescription(type.getDescription());
                    return vo;
                })
                .toList();
    }

    @Override
    public PageResult<QuestionRecommendationBatchListVO> listBatches(QuestionRecommendationQueryDTO query) {
        Long userId = requireCurrentUserId();
        QuestionRecommendationQueryDTO request = query == null ? new QuestionRecommendationQueryDTO() : query;
        long pageNo = normalizePageNo(request.getPageNo());
        long pageSize = normalizePageSize(request.getPageSize());
        String sourceType = StringUtils.hasText(request.getSourceType())
                ? normalizeSourceType(request.getSourceType()) : null;
        String status = StringUtils.hasText(request.getStatus())
                ? normalizeBatchStatus(request.getStatus()) : null;
        LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = new LambdaQueryWrapper<QuestionRecommendationBatch>()
                .eq(QuestionRecommendationBatch::getUserId, userId)
                .eq(QuestionRecommendationBatch::getDeleted, CommonConstants.NO)
                .eq(sourceType != null, QuestionRecommendationBatch::getSourceType, sourceType)
                .eq(status != null, QuestionRecommendationBatch::getStatus, status)
                .eq(request.getJobTargetId() != null, QuestionRecommendationBatch::getJobTargetId, request.getJobTargetId())
                .eq(request.getMatchReportId() != null, QuestionRecommendationBatch::getMatchReportId, request.getMatchReportId())
                .eq(request.getSkillProfileId() != null, QuestionRecommendationBatch::getSkillProfileId, request.getSkillProfileId())
                .eq(request.getStudyPlanId() != null, QuestionRecommendationBatch::getStudyPlanId, request.getStudyPlanId())
                .orderByDesc(QuestionRecommendationBatch::getUpdatedAt);
        long candidatePageSize = pageNo == 1L ? Math.max(pageSize, 10L) : pageSize;
        Page<QuestionRecommendationBatch> page = batchMapper.selectPage(Page.of(pageNo, candidatePageSize), wrapper);
        List<QuestionRecommendationBatchListVO> trustedRecords = page.getRecords().stream()
                .filter(this::hasTrustedBatchEvidence)
                .limit(pageSize)
                .map(this::toListVO)
                .toList();
        return PageResult.of(trustedRecords, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public QuestionRecommendationBatchDetailVO batchDetail(Long batchId) {
        QuestionRecommendationBatch batch = getOwnedBatch(batchId, requireCurrentUserId());
        assertTrustedBatchEvidence(batch);
        QuestionRecommendationBatchDetailVO vo = toDetailVO(batch);
        vo.setItems(batchItems(batchId));
        return vo;
    }

    @Override
    public List<QuestionRecommendationItemVO> batchItems(Long batchId) {
        QuestionRecommendationBatch batch = getOwnedBatch(batchId, requireCurrentUserId());
        assertTrustedBatchEvidence(batch);
        return itemMapper.selectList(new LambdaQueryWrapper<QuestionRecommendationItem>()
                        .eq(QuestionRecommendationItem::getBatchId, batch.getId())
                        .eq(QuestionRecommendationItem::getUserId, batch.getUserId())
                        .eq(QuestionRecommendationItem::getDeleted, CommonConstants.NO)
                        .orderByAsc(QuestionRecommendationItem::getSortOrder)
                        .orderByAsc(QuestionRecommendationItem::getId))
                .stream()
                .map(item -> toItemVO(item, batch))
                .toList();
    }

    @Override
    public List<QuestionRecommendationItemVO> recommendByJobTarget(Long targetJobId, Integer limit) {
        if (targetJobId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择目标岗位后再生成推荐题");
        }
        Long userId = requireCurrentUserId();
        List<QuestionRecommendationBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<QuestionRecommendationBatch>()
                .eq(QuestionRecommendationBatch::getUserId, userId)
                .eq(QuestionRecommendationBatch::getJobTargetId, targetJobId)
                .eq(QuestionRecommendationBatch::getStatus, QuestionRecommendationBatchStatus.SUCCESS.getCode())
                .eq(QuestionRecommendationBatch::getDeleted, CommonConstants.NO)
                .orderByDesc(QuestionRecommendationBatch::getUpdatedAt)
                .last("limit 10"));
        QuestionRecommendationBatch batch = batches.stream()
                .filter(this::hasTrustedBatchEvidence)
                .findFirst()
                .orElse(null);
        if (batch == null) {
            return List.of();
        }
        return listBatchItems(batch, normalizeRecommendationLimit(limit));
    }

    @Override
    public List<QuestionRecommendationItemVO> recommendBySkill(Long skillProfileId, String skillCode,
                                                               String skillName, Integer limit) {
        Long userId = requireCurrentUserId();
        LambdaQueryWrapper<QuestionRecommendationBatch> batchWrapper = new LambdaQueryWrapper<QuestionRecommendationBatch>()
                .eq(QuestionRecommendationBatch::getUserId, userId)
                .eq(skillProfileId != null, QuestionRecommendationBatch::getSkillProfileId, skillProfileId)
                .eq(QuestionRecommendationBatch::getStatus, QuestionRecommendationBatchStatus.SUCCESS.getCode())
                .eq(QuestionRecommendationBatch::getDeleted, CommonConstants.NO)
                .orderByDesc(QuestionRecommendationBatch::getUpdatedAt);
        List<QuestionRecommendationBatch> batches = batchMapper.selectList(batchWrapper);
        if (batches.isEmpty()) {
            return List.of();
        }
        int itemLimit = normalizeRecommendationLimit(limit);
        List<QuestionRecommendationItemVO> result = new java.util.ArrayList<>();
        for (QuestionRecommendationBatch batch : batches) {
            if (!hasTrustedBatchEvidence(batch)) {
                continue;
            }
            LambdaQueryWrapper<QuestionRecommendationItem> wrapper = new LambdaQueryWrapper<QuestionRecommendationItem>()
                    .eq(QuestionRecommendationItem::getBatchId, batch.getId())
                    .eq(QuestionRecommendationItem::getUserId, userId)
                    .eq(QuestionRecommendationItem::getDeleted, CommonConstants.NO)
                    .eq(StringUtils.hasText(skillCode), QuestionRecommendationItem::getSkillCode, skillCode)
                    .like(StringUtils.hasText(skillName), QuestionRecommendationItem::getSkillName, skillName)
                    .orderByAsc(QuestionRecommendationItem::getSortOrder)
                    .orderByAsc(QuestionRecommendationItem::getId)
                    .last("limit " + (itemLimit - result.size()));
            result.addAll(itemMapper.selectList(wrapper).stream().map(item -> toItemVO(item, batch)).toList());
            if (result.size() >= itemLimit) {
                break;
            }
        }
        return result;
    }

    private QuestionRecommendationGenerateVO generate(RecommendationRequest request, Long userId) {
        QuestionRecommendationBatch batch = createBatch(request, userId);
        GenerateQuestionRecommendationDTO aiRequest = buildAiRequest(batch, request, userId);
        batch.setRequestJson(toJson(aiRequest));
        batchMapper.updateById(batch);
        return executeBatch(batch, true);
    }

    private QuestionRecommendationGenerateVO submit(RecommendationRequest request, Long userId) {
        QuestionRecommendationBatch batch = createBatch(request, userId);
        GenerateQuestionRecommendationDTO aiRequest = buildAiRequest(batch, request, userId);
        batch.setRequestJson(toJson(aiRequest));
        batchMapper.updateById(batch);
        MqDispatchReceipt receipt = dispatchRecommendationGenerate(batch);
        if (receipt == null) {
            batch.setStatus(QuestionRecommendationBatchStatus.FAILED.getCode());
            batch.setErrorMessage("推荐题生成任务暂时提交失败，请稍后重试");
            batchMapper.updateById(batch);
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "推荐题生成任务暂时提交失败，请稍后重试");
        }
        return withAsyncReceipt(toGenerateVO(batchMapper.selectById(batch.getId())), receipt);
    }

    private MqDispatchReceipt dispatchRecommendationGenerate(QuestionRecommendationBatch batch) {
        return questionMqDispatcher
                .map(dispatcher -> dispatcher.dispatchRecommendationGenerateWithReceipt(batch.getId(), batch.getUserId()))
                .orElse(null);
    }

    private QuestionRecommendationGenerateVO executeBatch(QuestionRecommendationBatch batch, boolean failFast) {
        try {
            GenerateQuestionRecommendationDTO aiRequest = readAiRequest(batch);
            GenerateQuestionRecommendationVO aiResult = FeignResultUtils.unwrap(
                    aiRecommendationFeignClient.generate(aiRequest));
            validateAiResult(aiResult);
            replaceItems(batch, aiResult.getQuestions());
            batch.setStatus(QuestionRecommendationBatchStatus.SUCCESS.getCode());
            batch.setAiCallLogId(aiResult.getAiCallLogId());
            batch.setQuestionCount(aiResult.getQuestions().size());
            batch.setResultJson(firstText(aiResult.getRawResponse(), toJson(aiResult)));
            batch.setErrorMessage(null);
            batchMapper.updateById(batch);
            return toGenerateVO(batchMapper.selectById(batch.getId()));
        } catch (RuntimeException ex) {
            String failureMessage = userFacingGenerationError(ex);
            batch.setStatus(QuestionRecommendationBatchStatus.FAILED.getCode());
            batch.setErrorMessage(truncateErrorMessage(failureMessage));
            batchMapper.updateById(batch);
            if (!failFast) {
                return toGenerateVO(batchMapper.selectById(batch.getId()));
            }
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "推荐题生成暂时失败：" + batch.getErrorMessage());
        }
    }

    private GenerateQuestionRecommendationDTO readAiRequest(QuestionRecommendationBatch batch) {
        if (!StringUtils.hasText(batch.getRequestJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "推荐题生成记录暂时无法读取，请重新提交");
        }
        try {
            GenerateQuestionRecommendationDTO dto = objectMapper.readValue(
                    batch.getRequestJson(), GenerateQuestionRecommendationDTO.class);
            if (dto.getBatchId() == null || !dto.getBatchId().equals(batch.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "推荐题生成记录暂时无法读取，请重新提交");
            }
            if (dto.getUserId() == null || !dto.getUserId().equals(batch.getUserId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "推荐题生成记录暂时无法读取，请重新提交");
            }
            return dto;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "推荐题生成记录暂时无法读取，请重新提交");
        }
    }

    private QuestionRecommendationBatch createBatch(RecommendationRequest request, Long userId) {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setUserId(userId);
        batch.setSourceType(request.sourceType().getCode());
        batch.setSourceId(request.sourceId());
        batch.setJobTargetId(request.profile().getTargetJobId());
        batch.setMatchReportId(request.profile().getMatchReportId());
        batch.setSkillProfileId(request.profile().getProfileId());
        batch.setStudyPlanId(request.studyPlan() == null ? null : request.studyPlan().getPlanId());
        batch.setStrategy(request.strategy());
        batch.setQuestionCount(request.questionCount());
        batch.setStatus(QuestionRecommendationBatchStatus.GENERATING.getCode());
        batch.setRequestJson(toJson(requestSnapshot(request)));
        batchMapper.insert(batch);
        return batch;
    }

    private GenerateQuestionRecommendationDTO buildAiRequest(QuestionRecommendationBatch batch,
                                                             RecommendationRequest request,
                                                             Long userId) {
        GenerateQuestionRecommendationDTO dto = new GenerateQuestionRecommendationDTO();
        dto.setBatchId(batch.getId());
        dto.setUserId(userId);
        dto.setSourceType(batch.getSourceType());
        dto.setSourceId(batch.getSourceId());
        dto.setTargetJobId(batch.getJobTargetId());
        dto.setMatchReportId(batch.getMatchReportId());
        dto.setSkillProfileId(batch.getSkillProfileId());
        dto.setStudyPlanId(batch.getStudyPlanId());
        dto.setStrategy(batch.getStrategy());
        dto.setQuestionCount(batch.getQuestionCount());
        dto.setDifficultyPreference(request.difficultyPreference());
        dto.setTargetJobJson(toJson(targetJobSnapshot(request.profile())));
        dto.setMatchReportJson(toJson(matchReportSnapshot(request.profile())));
        dto.setSkillProfileJson(toJson(skillProfileSnapshot(request.profile())));
        dto.setSkillGapsJson(toJson(request.gaps().stream().map(this::gapSnapshot).toList()));
        dto.setStudyPlanJson(request.studyPlan() == null ? null : toJson(studyPlanSnapshot(request.studyPlan())));
        dto.setStudyTasksJson(request.studyPlan() == null ? null : toJson(studyTaskSnapshots(request.studyPlan())));
        return dto;
    }

    private void validateAiResult(GenerateQuestionRecommendationVO aiResult) {
        if (aiResult == null || aiResult.getQuestions() == null || aiResult.getQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "推荐题暂时没有生成有效题目，请稍后重试");
        }
    }

    private void replaceItems(QuestionRecommendationBatch batch, List<QuestionRecommendationDraftItemVO> drafts) {
        itemMapper.delete(new LambdaQueryWrapper<QuestionRecommendationItem>()
                .eq(QuestionRecommendationItem::getBatchId, batch.getId())
                .eq(QuestionRecommendationItem::getUserId, batch.getUserId()));
        int order = 1;
        for (QuestionRecommendationDraftItemVO draft : drafts) {
            if (draft == null || !StringUtils.hasText(draft.getTitle())
                    || !StringUtils.hasText(draft.getContent())) {
                continue;
            }
            QuestionRecommendationItem item = new QuestionRecommendationItem();
            item.setBatchId(batch.getId());
            item.setUserId(batch.getUserId());
            Long matchedQuestionId = matchExistingQuestion(draft);
            item.setQuestionId(matchedQuestionId);
            item.setQuestionTitle(draft.getTitle().trim());
            item.setQuestionContent(draft.getContent().trim());
            item.setQuestionType(firstText(draft.getQuestionType(), "SHORT_ANSWER"));
            item.setDifficulty(firstText(draft.getDifficulty(), DEFAULT_DIFFICULTY));
            item.setSkillCode(draft.getSkillCode());
            item.setSkillName(draft.getSkillName());
            item.setGapSeverity(draft.getGapSeverity());
            item.setRecommendReason(draft.getRecommendReason());
            item.setAnswerHint(draft.getAnswerHint());
            item.setEvaluatePoints(draft.getEvaluatePoints());
            item.setSortOrder(order++);
            boolean matched = matchedQuestionId != null;
            item.setMatchStatus(matched
                    ? QuestionRecommendationMatchStatus.MATCHED.getCode()
                    : QuestionRecommendationMatchStatus.UNMATCHED_DRAFT.getCode());
            item.setPracticeStatus(matched
                    ? QuestionRecommendationPracticeStatus.UNPRACTICED.getCode()
                    : QuestionRecommendationPracticeStatus.NOT_PRACTICABLE.getCode());
            itemMapper.insert(item);
        }
        if (order == 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "推荐题暂时没有生成有效题目，请稍后重试");
        }
    }

    private Long matchExistingQuestion(QuestionRecommendationDraftItemVO draft) {
        String keyword = firstText(draft.getSkillName(), draft.getTitle());
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, CommonConstants.YES)
                .eq(StringUtils.hasText(draft.getDifficulty()), Question::getDifficulty, draft.getDifficulty())
                .and(condition -> condition.like(Question::getTitle, keyword)
                        .or()
                        .like(Question::getContent, keyword)
                        .or()
                        .like(Question::getAnalysis, keyword)
                        .or()
                        .like(Question::getReferenceAnswer, keyword))
                .orderByDesc(Question::getIsHighFrequency)
                .orderByDesc(Question::getUpdatedAt)
                .last("limit 1");
        Question question = questionMapper.selectOne(wrapper);
        return question == null ? null : question.getId();
    }

    private InnerSkillProfileVO loadOwnedProfile(Long profileId, Long userId) {
        InnerSkillProfileVO profile = FeignResultUtils.unwrap(resumeProfileFeignClient.getSkillProfile(profileId));
        validateOwnedProfile(profile, userId);
        return profile;
    }

    private void validateOwnedProfile(InnerSkillProfileVO profile, Long userId) {
        if (profile == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像不存在或无权访问");
        }
        if (!userId.equals(profile.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像不存在或无权访问");
        }
        if (!"SUCCESS".equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像还未生成完成，暂时不能生成推荐题");
        }
        if (profile.getGapItems() == null || profile.getGapItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前能力画像暂无可训练短板，请先补充岗位或简历信息");
        }
    }

    private void validateOwnedPlan(InnerStudyPlanVO plan, Long userId) {
        if (plan == null || !userId.equals(plan.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划不存在或无权访问");
        }
        if (!"ACTIVE".equals(plan.getPlanStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划当前不可生成推荐题，请先确认计划状态");
        }
    }

    private List<InnerSkillGapItemVO> resolveSelectedGaps(InnerSkillProfileVO profile, List<Long> gapItemIds) {
        List<InnerSkillGapItemVO> gaps = profile.getGapItems().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(gap -> gap.getPriority() == null ? Integer.MAX_VALUE : gap.getPriority()))
                .toList();
        if (gapItemIds == null || gapItemIds.isEmpty()) {
            return gaps;
        }
        Set<Long> selectedIds = gapItemIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        List<InnerSkillGapItemVO> selected = gaps.stream()
                .filter(gap -> selectedIds.contains(gap.getId()))
                .toList();
        if (selected.isEmpty() || selected.size() != selectedIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "选择的能力短板不存在，请刷新后重试");
        }
        return selected;
    }

    private QuestionRecommendationBatch getOwnedBatch(Long batchId, Long userId) {
        if (batchId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择推荐题批次");
        }
        QuestionRecommendationBatch batch = batchMapper.selectOne(
                new LambdaQueryWrapper<QuestionRecommendationBatch>()
                        .eq(QuestionRecommendationBatch::getId, batchId)
                        .eq(QuestionRecommendationBatch::getUserId, userId)
                        .eq(QuestionRecommendationBatch::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "推荐题记录不存在或无权访问");
        }
        return batch;
    }

    private void assertTrustedBatchEvidence(QuestionRecommendationBatch batch) {
        if (!hasTrustedBatchEvidence(batch)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "推荐题依据的匹配报告证据待复核，请重新生成可信报告后再继续训练");
        }
    }

    private boolean hasTrustedBatchEvidence(QuestionRecommendationBatch batch) {
        if (batch == null) {
            return true;
        }
        try {
            if (isMatchReportSourceType(batch.getSourceType())) {
                Long matchReportId = effectiveMatchReportId(batch);
                if (matchReportId == null) {
                    return false;
                }
                InnerSkillProfileVO profile = FeignResultUtils.unwrap(
                        resumeProfileFeignClient.getSuccessSkillProfileByMatchReport(matchReportId));
                return profile != null
                        && batch.getUserId().equals(profile.getUserId())
                        && matchReportId.equals(profile.getMatchReportId());
            }
            if (batch.getSkillProfileId() != null) {
                InnerSkillProfileVO profile = FeignResultUtils.unwrap(
                        resumeProfileFeignClient.getSkillProfile(batch.getSkillProfileId()));
                return profile != null && batch.getUserId().equals(profile.getUserId());
            }
            return batch.getMatchReportId() == null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Long effectiveMatchReportId(QuestionRecommendationBatch batch) {
        if (batch.getMatchReportId() != null) {
            return batch.getMatchReportId();
        }
        return isMatchReportSourceType(batch.getSourceType()) ? batch.getSourceId() : null;
    }

    private boolean isMatchReportSourceType(String sourceType) {
        String value = String.valueOf(sourceType).trim().toUpperCase(Locale.ROOT);
        return QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode().equals(value)
                || "MATCH_REPORT".equals(value);
    }

    private List<QuestionRecommendationItemVO> listBatchItems(QuestionRecommendationBatch batch, int limit) {
        return itemMapper.selectList(new LambdaQueryWrapper<QuestionRecommendationItem>()
                        .eq(QuestionRecommendationItem::getBatchId, batch.getId())
                        .eq(QuestionRecommendationItem::getUserId, batch.getUserId())
                        .eq(QuestionRecommendationItem::getDeleted, CommonConstants.NO)
                        .orderByAsc(QuestionRecommendationItem::getSortOrder)
                        .orderByAsc(QuestionRecommendationItem::getId)
                        .last("limit " + limit))
                .stream()
                .map(item -> toItemVO(item, batch))
                .toList();
    }

    private QuestionRecommendationBatchListVO toListVO(QuestionRecommendationBatch batch) {
        QuestionRecommendationBatchListVO vo = new QuestionRecommendationBatchListVO();
        vo.setBatchId(batch.getId());
        vo.setUserId(batch.getUserId());
        vo.setSourceType(batch.getSourceType());
        vo.setSourceId(batch.getSourceId());
        vo.setJobTargetId(batch.getJobTargetId());
        vo.setMatchReportId(batch.getMatchReportId());
        vo.setSkillProfileId(batch.getSkillProfileId());
        vo.setStudyPlanId(batch.getStudyPlanId());
        vo.setStrategy(batch.getStrategy());
        vo.setQuestionCount(batch.getQuestionCount());
        vo.setStatus(batch.getStatus());
        vo.setAiCallLogId(batch.getAiCallLogId());
        vo.setErrorMessage(batch.getErrorMessage());
        vo.setTrustStatus(batchTrustStatus(batch));
        vo.setEvidenceSummary(batchEvidenceSummary(batch));
        vo.setFallback(batchFallback(batch));
        vo.setCreatedAt(batch.getCreatedAt());
        vo.setUpdatedAt(batch.getUpdatedAt());
        return vo;
    }

    private QuestionRecommendationBatchDetailVO toDetailVO(QuestionRecommendationBatch batch) {
        QuestionRecommendationBatchDetailVO vo = new QuestionRecommendationBatchDetailVO();
        QuestionRecommendationBatchListVO base = toListVO(batch);
        vo.setBatchId(base.getBatchId());
        vo.setUserId(base.getUserId());
        vo.setSourceType(base.getSourceType());
        vo.setSourceId(base.getSourceId());
        vo.setJobTargetId(base.getJobTargetId());
        vo.setMatchReportId(base.getMatchReportId());
        vo.setSkillProfileId(base.getSkillProfileId());
        vo.setStudyPlanId(base.getStudyPlanId());
        vo.setStrategy(base.getStrategy());
        vo.setQuestionCount(base.getQuestionCount());
        vo.setStatus(base.getStatus());
        vo.setAiCallLogId(base.getAiCallLogId());
        vo.setErrorMessage(base.getErrorMessage());
        vo.setTrustStatus(base.getTrustStatus());
        vo.setEvidenceSummary(base.getEvidenceSummary());
        vo.setFallback(base.getFallback());
        vo.setCreatedAt(base.getCreatedAt());
        vo.setUpdatedAt(base.getUpdatedAt());
        vo.setRequest(readJsonOrNull(batch.getRequestJson()));
        vo.setResult(readJsonOrNull(batch.getResultJson()));
        return vo;
    }

    private QuestionRecommendationGenerateVO toGenerateVO(QuestionRecommendationBatch batch) {
        QuestionRecommendationGenerateVO vo = new QuestionRecommendationGenerateVO();
        vo.setBatchId(batch.getId());
        vo.setSourceType(batch.getSourceType());
        vo.setSourceId(batch.getSourceId());
        vo.setStatus(batch.getStatus());
        vo.setQuestionCount(batch.getQuestionCount());
        vo.setAiCallLogId(batch.getAiCallLogId());
        vo.setErrorMessage(batch.getErrorMessage());
        vo.setTrustStatus(batchTrustStatus(batch));
        vo.setEvidenceSummary(batchEvidenceSummary(batch));
        vo.setFallback(batchFallback(batch));
        vo.setCreatedAt(batch.getCreatedAt());
        vo.setUpdatedAt(batch.getUpdatedAt());
        return vo;
    }

    private QuestionRecommendationGenerateVO withAsyncReceipt(QuestionRecommendationGenerateVO vo,
                                                              MqDispatchReceipt receipt) {
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

    private QuestionRecommendationItemVO toItemVO(QuestionRecommendationItem item, QuestionRecommendationBatch batch) {
        QuestionRecommendationItemVO vo = new QuestionRecommendationItemVO();
        vo.setId(item.getId());
        vo.setBatchId(item.getBatchId());
        vo.setQuestionId(item.getQuestionId());
        vo.setQuestionTitle(item.getQuestionTitle());
        vo.setQuestionContent(item.getQuestionContent());
        vo.setQuestionType(item.getQuestionType());
        vo.setDifficulty(item.getDifficulty());
        vo.setSkillCode(item.getSkillCode());
        vo.setSkillName(item.getSkillName());
        vo.setGapSeverity(item.getGapSeverity());
        vo.setRecommendReason(item.getRecommendReason());
        vo.setAnswerHint(item.getAnswerHint());
        vo.setEvaluatePoints(item.getEvaluatePoints());
        vo.setSortOrder(item.getSortOrder());
        boolean canPractice = item.getQuestionId() != null
                && QuestionRecommendationMatchStatus.MATCHED.getCode().equals(defaultMatchStatus(item));
        vo.setMatchStatus(defaultMatchStatus(item));
        vo.setPracticeStatus(defaultPracticeStatus(item, canPractice));
        vo.setCanPractice(canPractice);
        vo.setPracticeQuestionId(canPractice ? item.getQuestionId() : null);
        vo.setSourceType(batch == null ? null : batch.getSourceType());
        vo.setSourceId(batch == null ? null : batch.getSourceId());
        vo.setTrustStatus(itemTrustStatus(item, batch, canPractice));
        vo.setEvidenceSummary(itemEvidenceSummary(item, batch, canPractice));
        vo.setFallback(!canPractice);
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private String defaultMatchStatus(QuestionRecommendationItem item) {
        if (StringUtils.hasText(item.getMatchStatus())) {
            return item.getMatchStatus();
        }
        return item.getQuestionId() == null
                ? QuestionRecommendationMatchStatus.UNMATCHED_DRAFT.getCode()
                : QuestionRecommendationMatchStatus.MATCHED.getCode();
    }

    private String defaultPracticeStatus(QuestionRecommendationItem item, boolean canPractice) {
        if (!canPractice) {
            return QuestionRecommendationPracticeStatus.NOT_PRACTICABLE.getCode();
        }
        return StringUtils.hasText(item.getPracticeStatus())
                ? item.getPracticeStatus()
                : QuestionRecommendationPracticeStatus.UNPRACTICED.getCode();
    }

    private String batchTrustStatus(QuestionRecommendationBatch batch) {
        if (batch == null || !QuestionRecommendationBatchStatus.SUCCESS.getCode().equals(batch.getStatus())) {
            return TRUST_PARTIAL;
        }
        return batch.getAiCallLogId() == null ? TRUST_PARTIAL : TRUST_VERIFIED;
    }

    private Boolean batchFallback(QuestionRecommendationBatch batch) {
        return batch == null || !QuestionRecommendationBatchStatus.SUCCESS.getCode().equals(batch.getStatus())
                || batch.getSourceId() == null;
    }

    private String batchEvidenceSummary(QuestionRecommendationBatch batch) {
        if (batch == null) {
            return "推荐批次缺少上下文证据。";
        }
        String source = sourceEvidenceLabel(batch.getSourceType());
        String sourceId = batch.getSourceId() == null ? "推荐依据待确认" : "推荐依据已绑定";
        if (QuestionRecommendationBatchStatus.FAILED.getCode().equals(batch.getStatus())) {
            return source + " " + sourceId + " 生成失败：" + firstText(batch.getErrorMessage(), "失败原因待确认");
        }
        if (QuestionRecommendationBatchStatus.SUCCESS.getCode().equals(batch.getStatus())) {
            String generationRecord = batch.getAiCallLogId() == null ? "生成进度待补齐" : "生成结果已记录";
            return source + " " + sourceId + " · " + generationRecord + " · " + firstText(batch.getQuestionCount() == null ? null : batch.getQuestionCount() + " 道推荐题", "题量待确认");
        }
        return source + " " + sourceId + " 正在生成，结果可信度待确认。";
    }

    private String itemTrustStatus(QuestionRecommendationItem item, QuestionRecommendationBatch batch, boolean canPractice) {
        if (!canPractice) {
            return TRUST_FALLBACK;
        }
        if (batch == null || batch.getAiCallLogId() == null) {
            return TRUST_PARTIAL;
        }
        return StringUtils.hasText(item.getRecommendReason())
                || StringUtils.hasText(item.getAnswerHint())
                || StringUtils.hasText(item.getEvaluatePoints())
                ? TRUST_VERIFIED
                : TRUST_PARTIAL;
    }

    private String itemEvidenceSummary(QuestionRecommendationItem item, QuestionRecommendationBatch batch,
                                       boolean canPractice) {
        String source = batch == null ? "推荐来源" : sourceEvidenceLabel(batch.getSourceType());
        String sourceId = batch == null || batch.getSourceId() == null ? "未绑定上下文" : "#" + batch.getSourceId();
        String skill = firstText(item.getSkillName(), item.getSkillCode(), "未标注技能");
        if (!canPractice) {
            return source + " " + sourceId + " · " + skill + " · AI 推荐方向尚未匹配正式题库。";
        }
        String reason = firstText(item.getRecommendReason(), item.getAnswerHint(), item.getEvaluatePoints(), "已匹配正式题库，可直接练习。");
        return source + " " + sourceId + " · " + skill + " · " + reason;
    }

    private String sourceEvidenceLabel(String sourceType) {
        if (QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode().equals(sourceType)) {
            return "来自简历匹配报告";
        }
        if (QuestionRecommendationSourceType.STUDY_PLAN.getCode().equals(sourceType)) {
            return "来自学习计划";
        }
        if (QuestionRecommendationSourceType.JD_GAP.getCode().equals(sourceType)) {
            return "来自能力画像/岗位要求短板";
        }
        return "来自推荐上下文";
    }

    private Map<String, Object> requestSnapshot(RecommendationRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceType", request.sourceType().getCode());
        snapshot.put("sourceId", request.sourceId());
        snapshot.put("questionCount", request.questionCount());
        snapshot.put("difficultyPreference", request.difficultyPreference());
        snapshot.put("strategy", request.strategy());
        snapshot.put("skillProfileId", request.profile().getProfileId());
        snapshot.put("gapItemIds", request.gaps().stream().map(InnerSkillGapItemVO::getId).toList());
        snapshot.put("studyPlanId", request.studyPlan() == null ? null : request.studyPlan().getPlanId());
        return snapshot;
    }

    private Map<String, Object> targetJobSnapshot(InnerSkillProfileVO profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("targetJobId", profile.getTargetJobId());
        snapshot.put("jobTitle", profile.getTargetJobTitle());
        snapshot.put("companyName", profile.getTargetCompanyName());
        snapshot.put("jobLevel", profile.getTargetJobLevel());
        snapshot.put("jdSource", profile.getTargetJdSource());
        return snapshot;
    }

    private Map<String, Object> matchReportSnapshot(InnerSkillProfileVO profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("matchReportId", profile.getMatchReportId());
        snapshot.put("sourceType", profile.getSourceType());
        snapshot.put("sourceBizId", profile.getSourceBizId());
        return snapshot;
    }

    private Map<String, Object> skillProfileSnapshot(InnerSkillProfileVO profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("profileId", profile.getProfileId());
        snapshot.put("profileName", profile.getProfileName());
        snapshot.put("overallLevel", profile.getOverallLevel());
        snapshot.put("overallScore", profile.getOverallScore());
        snapshot.put("summary", profile.getSummary());
        snapshot.put("rawResult", readJsonOrNull(profile.getRawResultJson()));
        return snapshot;
    }

    private Map<String, Object> gapSnapshot(InnerSkillGapItemVO gap) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", gap.getId());
        snapshot.put("skillName", gap.getSkillName());
        snapshot.put("category", gap.getCategory());
        snapshot.put("targetLevel", gap.getTargetLevel());
        snapshot.put("currentLevel", gap.getCurrentLevel());
        snapshot.put("gapLevel", gap.getGapLevel());
        snapshot.put("severity", gap.getSeverity());
        snapshot.put("confidence", gap.getConfidence());
        snapshot.put("gapDescription", gap.getGapDescription());
        snapshot.put("evidenceSources", readJsonOrNull(gap.getEvidenceSourcesJson()));
        snapshot.put("recommendedActions", readJsonOrNull(gap.getRecommendedActionsJson()));
        snapshot.put("priority", gap.getPriority());
        return snapshot;
    }

    private Map<String, Object> studyPlanSnapshot(InnerStudyPlanVO plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planId", plan.getPlanId());
        snapshot.put("sourceType", plan.getSourceType());
        snapshot.put("sourceId", plan.getSourceId());
        snapshot.put("planTitle", plan.getPlanTitle());
        snapshot.put("planSummary", plan.getPlanSummary());
        snapshot.put("planStatus", plan.getPlanStatus());
        snapshot.put("durationDays", plan.getDurationDays());
        snapshot.put("dailyMinutes", plan.getDailyMinutes());
        snapshot.put("startDate", plan.getStartDate());
        snapshot.put("result", readJsonOrNull(plan.getResultJson()));
        return snapshot;
    }

    private List<Map<String, Object>> studyTaskSnapshots(InnerStudyPlanVO plan) {
        if (plan.getTasks() == null) {
            return List.of();
        }
        return plan.getTasks().stream().map(this::studyTaskSnapshot).toList();
    }

    private Map<String, Object> studyTaskSnapshot(InnerStudyTaskVO task) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("skillGapItemId", task.getSkillGapItemId());
        snapshot.put("stageNo", task.getStageNo());
        snapshot.put("plannedDate", task.getPlannedDate());
        snapshot.put("stageTitle", task.getStageTitle());
        snapshot.put("knowledgePoint", task.getKnowledgePoint());
        snapshot.put("taskTitle", task.getTaskTitle());
        snapshot.put("taskDescription", task.getTaskDescription());
        snapshot.put("taskType", task.getTaskType());
        snapshot.put("priority", task.getPriority());
        snapshot.put("estimatedMinutes", task.getEstimatedMinutes());
        snapshot.put("acceptanceCriteria", task.getAcceptanceCriteria());
        snapshot.put("relatedTags", readJsonOrNull(task.getRelatedTagsJson()));
        return snapshot;
    }

    private String normalizeSourceType(String sourceType) {
        String value = sourceType.trim().toUpperCase(Locale.ROOT);
        // Deprecated compatibility aliases for old clients. Keep the persisted/primary enum values unchanged.
        if ("GAP".equals(value)) {
            return QuestionRecommendationSourceType.JD_GAP.getCode();
        }
        if ("MATCH_REPORT".equals(value)) {
            return QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode();
        }
        for (QuestionRecommendationSourceType type : QuestionRecommendationSourceType.values()) {
            if (type.getCode().equals(value)) {
                return value;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR,
                "暂不支持该推荐题来源，请刷新页面后重试");
    }

    private String normalizeBatchStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        for (QuestionRecommendationBatchStatus item : QuestionRecommendationBatchStatus.values()) {
            if (item.getCode().equals(value)) {
                return value;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持该推荐题状态筛选，请刷新页面后重试");
    }

    private int normalizeQuestionCount(Integer count) {
        if (count == null) {
            return 5;
        }
        if (count < 1 || count > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "题目数量需在 1 到 20 道之间");
        }
        return count;
    }

    private String normalizeDifficulty(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return DEFAULT_DIFFICULTY;
        }
        String value = difficulty.trim().toUpperCase(Locale.ROOT);
        if (!List.of("EASY", "MEDIUM", "HARD").contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持该难度偏好，请刷新页面后重试");
        }
        return value;
    }

    private String normalizeStrategy(String strategy) {
        return StringUtils.hasText(strategy) ? strategy.trim().toUpperCase(Locale.ROOT) : DEFAULT_STRATEGY;
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

    private int normalizeRecommendationLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private JsonNode readJsonOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "推荐题生成记录暂时无法保存，请稍后重试");
        }
    }

    private String truncateErrorMessage(String message) {
        String value = StringUtils.hasText(message) ? message : "推荐题生成暂时失败，请稍后重试";
        return value.length() <= MAX_ERROR_MESSAGE_LENGTH ? value : value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String userFacingGenerationError(RuntimeException ex) {
        String message = ex.getMessage();
        String lowerMessage = StringUtils.hasText(message) ? message.toLowerCase(Locale.ROOT) : "";
        if (lowerMessage.contains("json") || lowerMessage.contains("parse")) {
            return "推荐题结果暂时无法整理，请稍后重试";
        }
        if (lowerMessage.contains("load balancer")
                || lowerMessage.contains("codecoachai-ai")
                || lowerMessage.contains("feign")
                || lowerMessage.contains("connection")
                || lowerMessage.contains("503")) {
            return "推荐题生成服务暂时不可用，请稍后重试";
        }
        if (ex instanceof BusinessException && StringUtils.hasText(message)) {
            return message;
        }
        return "推荐题生成暂时失败，请稍后重试";
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先登录后再生成推荐题");
        }
    }

    private record RecommendationRequest(
            QuestionRecommendationSourceType sourceType,
            Long sourceId,
            InnerSkillProfileVO profile,
            List<InnerSkillGapItemVO> gaps,
            InnerStudyPlanVO studyPlan,
            Integer questionCount,
            String difficultyPreference,
            String strategy) {
    }
}
