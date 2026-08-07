package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.dto.BatchQuestionReviewApproveDTO;
import com.codecoachai.question.domain.dto.BatchQuestionReviewRejectDTO;
import com.codecoachai.question.domain.dto.QuestionReviewApproveDTO;
import com.codecoachai.question.domain.dto.QuestionReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionReviewRejectDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionCategory;
import com.codecoachai.question.domain.entity.QuestionGroup;
import com.codecoachai.question.domain.entity.QuestionReview;
import com.codecoachai.question.domain.entity.QuestionTag;
import com.codecoachai.question.domain.entity.QuestionTagRelation;
import com.codecoachai.question.domain.enums.QuestionReviewStatus;
import com.codecoachai.question.domain.vo.AiQuestionGenerateResultVO;
import com.codecoachai.question.domain.vo.BatchQuestionReviewFailureVO;
import com.codecoachai.question.domain.vo.BatchQuestionReviewResultVO;
import com.codecoachai.question.domain.vo.QuestionReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionReviewListVO;
import com.codecoachai.question.feign.AiQuestionFeignClient;
import com.codecoachai.question.feign.dto.GenerateQuestionDraftDTO;
import com.codecoachai.question.feign.vo.GenerateQuestionDraftVO;
import com.codecoachai.question.feign.vo.QuestionDraftItemVO;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionReviewMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
import com.codecoachai.question.mq.QuestionMqDispatcher;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.codecoachai.question.service.QuestionReviewService;
import com.codecoachai.question.util.QuestionReviewRawPayloadUtils;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionReviewServiceImpl implements QuestionReviewService {

    private static final int MAX_GENERATE_COUNT = 20;
    private static final int MAX_BATCH_REVIEW_COUNT = 100;

    private final AiQuestionFeignClient aiQuestionFeignClient;
    private final QuestionReviewMapper questionReviewMapper;
    private final QuestionMapper questionMapper;
    private final QuestionCategoryMapper categoryMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionTagMapper tagMapper;
    private final QuestionTagRelationMapper tagRelationMapper;
    private final QuestionDuplicateService questionDuplicateService;
    private final QuestionEmbeddingIndexService questionEmbeddingIndexService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final QuestionMqDispatcher questionMqDispatcher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiQuestionGenerateResultVO generate(AiQuestionGenerateRequestDTO dto) {
        validateGenerateRequest(dto);
        Long adminUserId = SecurityAssert.requireLoginUserId();
        String batchId = "QG" + UUID.randomUUID().toString().replace("-", "");
        GenerateQuestionDraftDTO aiRequest = toAiRequest(batchId, adminUserId, dto);
        GenerateQuestionDraftVO aiResponse = FeignResultUtils.unwrap(
                aiQuestionFeignClient.generateQuestions(aiRequest));
        if (aiResponse == null || aiResponse.getQuestions() == null || aiResponse.getQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI question generation returned no questions");
        }
        List<Long> reviewIds = new ArrayList<>();
        String rawAiResultJson = toJson(QuestionReviewRawPayloadUtils.buildMinimizedMetadata(
                aiResponse.getAiCallLogId(),
                batchId,
                aiResponse.getQuestions().size()));
        for (QuestionDraftItemVO item : aiResponse.getQuestions()) {
            QuestionReview review = toReview(dto, batchId, adminUserId, aiResponse.getAiCallLogId(),
                    rawAiResultJson, item);
            questionReviewMapper.insert(review);
            reviewIds.add(review.getId());
        }
        AiQuestionGenerateResultVO result = new AiQuestionGenerateResultVO();
        result.setBatchId(batchId);
        result.setGeneratedCount(reviewIds.size());
        result.setReviewIds(reviewIds);
        result.setAiCallLogId(aiResponse.getAiCallLogId());
        return result;
    }

    @Override
    public AiQuestionGenerateResultVO submitGenerate(AiQuestionGenerateRequestDTO dto) {
        validateGenerateRequest(dto);
        Long adminUserId = SecurityAssert.requireLoginUserId();
        String batchId = "QG" + UUID.randomUUID().toString().replace("-", "");
        MqDispatchReceipt receipt = questionMqDispatcher.dispatchGenerateWithReceipt(
                batchId,
                adminUserId,
                defaultText(dto.getKnowledgePoint(), dto.getTechnologyStack(), dto.getTargetPosition(), "Java 后端面试题"),
                defaultText(dto.getDifficulty(), "MEDIUM"),
                dto.getCount() == null ? 5 : dto.getCount(),
                buildGenerateTags(dto),
                dto.getTargetPosition(),
                dto.getTechnologyStack(),
                dto.getKnowledgePoint(),
                defaultText(dto.getQuestionType(), "SHORT_ANSWER"),
                dto.getExperienceYears(),
                dto.getExperienceYears() == null ? null : String.valueOf(dto.getExperienceYears()),
                defaultBoolean(dto.getGenerateReferenceAnswer(), true),
                defaultBoolean(dto.getGenerateFollowUps(), true),
                defaultBoolean(dto.getGenerateTagSuggestions(), true),
                defaultBoolean(dto.getGenerateCategorySuggestion(), true),
                buildQuestionGenerateExtraRequirements(dto.getExtraRequirements())
        );
        AiQuestionGenerateResultVO result = new AiQuestionGenerateResultVO();
        result.setBatchId(batchId);
        result.setGeneratedCount(0);
        result.setReviewIds(Collections.emptyList());
        result.setAsyncBizType("question.generate");
        result.setAsyncBizId(batchId);
        if (receipt == null) {
            result.setAsyncSendStatus("FAILED");
            return result;
        }
        result.setAsyncMessageId(receipt.getMessageId());
        result.setAsyncTraceId(receipt.getTraceId());
        result.setAsyncBizType(defaultText(receipt.getBizType(), "question.generate"));
        result.setAsyncBizId(defaultText(receipt.getBizId(), batchId));
        result.setAsyncSendStatus(defaultText(receipt.getSendStatus(), "SENT"));
        return result;
    }

    @Override
    public PageResult<QuestionReviewListVO> pageReviews(QuestionReviewQueryDTO query) {
        QuestionReviewQueryDTO safeQuery = query == null ? new QuestionReviewQueryDTO() : query;
        Page<QuestionReview> page = questionReviewMapper.selectPage(
                Page.of(defaultPage(safeQuery.getPageNo()), defaultSize(safeQuery.getPageSize())),
                buildQueryWrapper(safeQuery));
        return PageResult.of(page.getRecords().stream().map(this::toListVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QuestionReviewDetailVO getReview(Long id) {
        return toDetailVO(getReviewOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionReviewDetailVO approve(Long id, QuestionReviewApproveDTO dto) {
        QuestionReview review = getReviewOrThrow(id);
        if (!QuestionReviewStatus.PENDING.name().equals(review.getReviewStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question review status has changed");
        }
        ApprovedQuestionPayload payload = buildApprovedPayload(review, dto);
        validateQuestionRefs(payload.categoryId(), payload.groupId(), payload.tagIds());

        Long reviewerId = SecurityAssert.requireLoginUserId();
        LocalDateTime reviewedAt = LocalDateTime.now();
        String editedContentJson = hasEditedFields(dto)
                ? toJson(payload.toMap(dto == null ? null : dto.getEditedReason()))
                : null;
        QuestionReview claim = new QuestionReview();
        claim.setReviewStatus(QuestionReviewStatus.APPROVED.name());
        claim.setReviewerId(reviewerId);
        claim.setReviewedAt(reviewedAt);
        claim.setEditedContentJson(editedContentJson);
        int claimed = questionReviewMapper.update(claim, new LambdaUpdateWrapper<QuestionReview>()
                .eq(QuestionReview::getId, id)
                .eq(QuestionReview::getReviewStatus, QuestionReviewStatus.PENDING.name()));
        if (claimed != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question review status has changed");
        }

        Question question = new Question();
        question.setTitle(payload.title());
        question.setContent(payload.content());
        question.setReferenceAnswer(payload.referenceAnswer());
        question.setAnalysis(payload.analysis());
        applyQuestionFingerprints(question);
        question.setCategoryId(payload.categoryId());
        question.setGroupId(payload.groupId());
        question.setDifficulty(payload.difficulty());
        question.setQuestionType(payload.questionType());
        question.setExperienceLevel(payload.experienceLevel());
        question.setIsHighFrequency(payload.isHighFrequency());
        question.setStatus(payload.status());
        questionMapper.insert(question);
        insertTagRelations(question.getId(), payload.tagIds());

        QuestionReview update = new QuestionReview();
        update.setApprovedQuestionId(question.getId());
        int affected = questionReviewMapper.update(update, new LambdaUpdateWrapper<QuestionReview>()
                .eq(QuestionReview::getId, id)
                .eq(QuestionReview::getReviewStatus, QuestionReviewStatus.APPROVED.name()));
        if (affected != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question review status has changed");
        }
        syncQuestionDuplicateCheckAfterCommit(question.getId(), reviewerId);
        syncQuestionEmbeddingAfterCommit(question.getId(), CommonConstants.YES.equals(question.getStatus()));
        syncQuestionSearchAfterCommit(question.getId(), reviewerId, CommonConstants.YES.equals(question.getStatus()));
        return getReview(id);
    }

    @Override
    public QuestionReviewDetailVO reject(Long id, QuestionReviewRejectDTO dto) {
        Long reviewerId = SecurityAssert.requireLoginUserId();
        QuestionReview update = new QuestionReview();
        update.setReviewStatus(QuestionReviewStatus.REJECTED.name());
        update.setRejectReason(dto.getRejectReason());
        update.setReviewerId(reviewerId);
        update.setReviewedAt(LocalDateTime.now());
        int affected = questionReviewMapper.update(update, new LambdaUpdateWrapper<QuestionReview>()
                .eq(QuestionReview::getId, id)
                .eq(QuestionReview::getReviewStatus, QuestionReviewStatus.PENDING.name()));
        if (affected != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question review status has changed");
        }
        return getReview(id);
    }

    @Override
    public QuestionReviewDetailVO cancel(Long id, QuestionReviewRejectDTO dto) {
        Long reviewerId = SecurityAssert.requireLoginUserId();
        QuestionReview update = new QuestionReview();
        update.setReviewStatus(QuestionReviewStatus.CANCELLED.name());
        update.setRejectReason(resolveCancelReason(dto));
        update.setReviewerId(reviewerId);
        update.setReviewedAt(LocalDateTime.now());
        int affected = questionReviewMapper.update(update, new LambdaUpdateWrapper<QuestionReview>()
                .eq(QuestionReview::getId, id)
                .eq(QuestionReview::getReviewStatus, QuestionReviewStatus.PENDING.name()));
        if (affected != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question review status has changed");
        }
        return getReview(id);
    }

    @Override
    public BatchQuestionReviewResultVO batchApprove(BatchQuestionReviewApproveDTO dto) {
        validateBatchReviewIds(dto == null ? null : dto.getReviewIds());
        BatchQuestionReviewResultVO result = newBatchResult(dto.getReviewIds());
        for (Long reviewId : dto.getReviewIds()) {
            processBatchItem(result, reviewId, () -> approve(reviewId, dto.getApproveData()));
        }
        finishBatchResult(result);
        return result;
    }

    @Override
    public BatchQuestionReviewResultVO batchReject(BatchQuestionReviewRejectDTO dto) {
        validateBatchReviewIds(dto == null ? null : dto.getReviewIds());
        BatchQuestionReviewResultVO result = newBatchResult(dto.getReviewIds());
        for (Long reviewId : dto.getReviewIds()) {
            processBatchItem(result, reviewId, () -> reject(reviewId, toRejectDTO(dto.getRejectReason())));
        }
        finishBatchResult(result);
        return result;
    }

    private void validateGenerateRequest(AiQuestionGenerateRequestDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        int count = dto.getCount() == null ? 5 : dto.getCount();
        if (count < 1 || count > MAX_GENERATE_COUNT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成题目数量需在 1 到 20 道之间");
        }
    }

    private void validateBatchReviewIds(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择待审核题目");
        }
        if (reviewIds.size() > MAX_BATCH_REVIEW_COUNT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多处理 100 道待审核题目");
        }
        if (reviewIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "待审核题目参数不完整，请刷新后重试");
        }
    }

    private QuestionReviewRejectDTO toRejectDTO(String rejectReason) {
        QuestionReviewRejectDTO rejectDTO = new QuestionReviewRejectDTO();
        rejectDTO.setRejectReason(rejectReason);
        return rejectDTO;
    }

    private String resolveCancelReason(QuestionReviewRejectDTO dto) {
        String reason = dto == null ? null : dto.getRejectReason();
        if (!StringUtils.hasText(reason)) {
            return "Admin cancelled draft";
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "取消原因不能超过 500 字");
        }
        return trimmed;
    }

    private BatchQuestionReviewResultVO newBatchResult(List<Long> reviewIds) {
        BatchQuestionReviewResultVO result = new BatchQuestionReviewResultVO();
        result.setTotal(reviewIds.size());
        return result;
    }

    private void processBatchItem(BatchQuestionReviewResultVO result, Long reviewId, Runnable action) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
            result.getSuccessIds().add(reviewId);
        } catch (Exception ex) {
            log.warn("Batch question review operation failed reviewId={}", reviewId, ex);
            BatchQuestionReviewFailureVO failure = new BatchQuestionReviewFailureVO();
            failure.setReviewId(reviewId);
            failure.setReason("Question review failed");
            result.getFailures().add(failure);
        }
    }

    private void finishBatchResult(BatchQuestionReviewResultVO result) {
        result.setSuccessCount(result.getSuccessIds().size());
        result.setFailureCount(result.getFailures().size());
    }

    private void syncQuestionSearchAfterCommit(Long questionId, Long userId, boolean upsert) {
        String op = upsert ? "UPSERT" : "DELETE";
        Runnable action = () -> {
            if (upsert) {
                if (!questionMqDispatcher.dispatchQuestionSearchUpsert(questionId, userId)) {
                    log.warn("Question review after-commit sync returned false syncType=question_search_sync questionId={} op={} reason={}",
                            questionId, op, "dispatcher returned false");
                }
            } else {
                if (!questionMqDispatcher.dispatchQuestionSearchDelete(questionId, userId)) {
                    log.warn("Question review after-commit sync returned false syncType=question_search_sync questionId={} op={} reason={}",
                            questionId, op, "dispatcher returned false");
                }
            }
        };
        Runnable safeAction = () -> runAfterCommitSafely("question_search_sync", questionId, op, action);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeAction.run();
            }
        });
    }

    private void syncQuestionEmbeddingAfterCommit(Long questionId, boolean upsert) {
        String op = upsert ? "UPSERT" : "DELETE";
        Runnable action = () -> {
            if (upsert) {
                questionEmbeddingIndexService.indexQuestion(questionId);
            } else {
                questionEmbeddingIndexService.deleteQuestion(questionId);
            }
        };
        Runnable safeAction = () -> runAfterCommitSafely("question_embedding_sync", questionId, op, action);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeAction.run();
            }
        });
    }

    private void syncQuestionDuplicateCheckAfterCommit(Long questionId, Long userId) {
        Runnable action = () -> questionDuplicateService.checkDuplicateForQuestion(questionId, userId);
        Runnable safeAction = () -> runAfterCommitSafely("question_duplicate_check", questionId, "CHECK", action);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeAction.run();
            }
        });
    }

    private void runAfterCommitSafely(String syncType, Long questionId, String op, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Question review after-commit sync failed syncType={} questionId={} op={} reason={}",
                    syncType, questionId, op, buildAfterCommitFailureReason(ex), ex);
        }
    }

    private String buildAfterCommitFailureReason(Exception ex) {
        if (ex == null) {
            return "unknown";
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private GenerateQuestionDraftDTO toAiRequest(String batchId, Long adminUserId, AiQuestionGenerateRequestDTO dto) {
        GenerateQuestionDraftDTO request = new GenerateQuestionDraftDTO();
        request.setBatchId(batchId);
        request.setAdminUserId(adminUserId);
        request.setTargetPosition(dto.getTargetPosition());
        request.setTechnologyStack(dto.getTechnologyStack());
        request.setKnowledgePoint(dto.getKnowledgePoint());
        request.setQuestionType(defaultText(dto.getQuestionType(), "SHORT_ANSWER"));
        request.setDifficulty(defaultText(dto.getDifficulty(), "MEDIUM"));
        request.setExperienceYears(dto.getExperienceYears());
        request.setCount(dto.getCount() == null ? 5 : dto.getCount());
        request.setGenerateReferenceAnswer(dto.getGenerateReferenceAnswer());
        request.setGenerateFollowUps(dto.getGenerateFollowUps());
        request.setGenerateTagSuggestions(dto.getGenerateTagSuggestions());
        request.setGenerateCategorySuggestion(dto.getGenerateCategorySuggestion());
        request.setExtraRequirements(buildQuestionGenerateExtraRequirements(dto.getExtraRequirements()));
        return request;
    }

    private List<String> buildGenerateTags(AiQuestionGenerateRequestDTO dto) {
        List<String> tags = new ArrayList<>();
        if (dto == null) {
            return tags;
        }
        for (String value : new String[] { dto.getTechnologyStack(), dto.getKnowledgePoint(), dto.getTargetPosition() }) {
            if (StringUtils.hasText(value)) {
                tags.add(value.trim());
            }
        }
        return tags.stream().distinct().toList();
    }

    private Boolean defaultBoolean(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private String buildQuestionGenerateExtraRequirements(String extraRequirements) {
        String qualityRules = """
                请使用中文输出题干、参考答案、解析、分类建议和问题组建议。
                每道题标题必须具体描述考点，不要使用“核心面试题 1/2/3”这类流水号标题。
                同一批题目之间需要覆盖不同子考点或不同业务场景，避免只替换序号造成重复题。
                分类建议优先使用：Java基础、集合、并发、JVM、Spring Boot、MySQL、Redis、微服务、设计模式、项目场景。
                问题组建议要贴近考点，例如 HashMap、JVM GC、线程池、MySQL索引、Redis缓存一致性、分布式锁。
                """;
        if (!StringUtils.hasText(extraRequirements)) {
            return qualityRules;
        }
        return extraRequirements + "\n" + qualityRules;
    }

    private QuestionReview toReview(AiQuestionGenerateRequestDTO request, String batchId, Long adminUserId,
                                    Long aiCallLogId, String rawAiResultJson, QuestionDraftItemVO item) {
        if (!StringUtils.hasText(item.getTitle()) || !StringUtils.hasText(item.getContent())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI question draft missing title or content");
        }
        QuestionReview review = new QuestionReview();
        review.setBatchId(batchId);
        review.setCreatedBy(adminUserId);
        review.setReviewStatus(QuestionReviewStatus.PENDING.name());
        review.setAiCallLogId(aiCallLogId);
        review.setTargetPosition(request.getTargetPosition());
        review.setTechnologyStack(request.getTechnologyStack());
        review.setKnowledgePoint(request.getKnowledgePoint());
        review.setQuestionType(defaultText(item.getQuestionType(),
                defaultText(request.getQuestionType(), "SHORT_ANSWER")));
        review.setDifficulty(defaultText(item.getDifficulty(), defaultText(request.getDifficulty(), "MEDIUM")));
        review.setExperienceYears(request.getExperienceYears());
        review.setRawAiResultJson(rawAiResultJson);
        review.setQuestionTitle(item.getTitle());
        review.setQuestionContent(item.getContent());
        review.setReferenceAnswer(item.getReferenceAnswer());
        review.setAnalysis(item.getAnalysis());
        review.setFollowUpQuestionsJson(toJson(item.getFollowUpQuestions()));
        review.setTagSuggestionsJson(toJson(item.getTagSuggestions()));
        review.setCategorySuggestion(item.getCategorySuggestion());
        review.setGroupSuggestion(item.getGroupSuggestion());
        return review;
    }

    private LambdaQueryWrapper<QuestionReview> buildQueryWrapper(QuestionReviewQueryDTO query) {
        return new LambdaQueryWrapper<QuestionReview>()
                .eq(StringUtils.hasText(query.getReviewStatus()), QuestionReview::getReviewStatus,
                        query.getReviewStatus())
                .eq(StringUtils.hasText(query.getBatchId()), QuestionReview::getBatchId, query.getBatchId())
                .eq(StringUtils.hasText(query.getQuestionType()), QuestionReview::getQuestionType,
                        query.getQuestionType())
                .eq(StringUtils.hasText(query.getDifficulty()), QuestionReview::getDifficulty, query.getDifficulty())
                .like(StringUtils.hasText(query.getTechnologyStack()), QuestionReview::getTechnologyStack,
                        query.getTechnologyStack())
                .like(StringUtils.hasText(query.getKnowledgePoint()), QuestionReview::getKnowledgePoint,
                        query.getKnowledgePoint())
                .and(StringUtils.hasText(query.getKeyword()), condition -> condition
                        .like(QuestionReview::getQuestionTitle, query.getKeyword())
                        .or()
                        .like(QuestionReview::getQuestionContent, query.getKeyword())
                        .or()
                        .like(QuestionReview::getKnowledgePoint, query.getKeyword()))
                .orderByDesc(QuestionReview::getCreatedAt);
    }

    private QuestionReview getReviewOrThrow(Long id) {
        QuestionReview review = questionReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question review not found");
        }
        return review;
    }

    private ApprovedQuestionPayload buildApprovedPayload(QuestionReview review, QuestionReviewApproveDTO dto) {
        List<Long> tagIds = dto != null && dto.getTagIds() != null
                ? dto.getTagIds()
                : parseLongList(review.getTagIdsJson());
        String title = defaultText(dto == null ? null : dto.getTitle(), review.getQuestionTitle());
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写题目标题");
        }
        Long categoryId = resolveCategoryId(review,
                dto != null && dto.getCategoryId() != null ? dto.getCategoryId() : review.getCategoryId());
        Long groupId = resolveGroupId(review,
                dto != null && dto.getGroupId() != null ? dto.getGroupId() : review.getGroupId(), categoryId);
        if (tagIds == null || tagIds.isEmpty()) {
            tagIds = resolveTagIds(review);
        }
        return new ApprovedQuestionPayload(
                title,
                defaultText(dto == null ? null : dto.getContent(), review.getQuestionContent()),
                defaultText(dto == null ? null : dto.getReferenceAnswer(), review.getReferenceAnswer()),
                defaultText(dto == null ? null : dto.getAnalysis(), review.getAnalysis()),
                defaultText(dto == null ? null : dto.getDifficulty(), review.getDifficulty(), "MEDIUM"),
                defaultText(dto == null ? null : dto.getQuestionType(), review.getQuestionType(), "SHORT_ANSWER"),
                categoryId,
                groupId,
                tagIds,
                dto != null && dto.getStatus() != null ? dto.getStatus() : CommonConstants.YES,
                dto != null && dto.getIsHighFrequency() != null ? dto.getIsHighFrequency() : CommonConstants.NO,
                defaultText(dto == null ? null : dto.getExperienceLevel(),
                        review.getExperienceYears() == null ? null : String.valueOf(review.getExperienceYears()))
        );
    }

    private Long resolveCategoryId(QuestionReview review, Long selectedId) {
        if (selectedId != null) {
            return selectedId;
        }
        String searchText = lookupText(review.getCategorySuggestion(), review.getTechnologyStack(),
                review.getKnowledgePoint(), review.getQuestionTitle(), review.getQuestionContent());
        List<QuestionCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<QuestionCategory>()
                .eq(QuestionCategory::getStatus, CommonConstants.YES));
        for (QuestionCategory category : categories) {
            if (containsNormalized(searchText, category.getCategoryName())) {
                return category.getId();
            }
        }
        for (String candidateName : categoryCandidates(searchText)) {
            Long id = findCategoryId(categories, candidateName);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private Long resolveGroupId(QuestionReview review, Long selectedId, Long categoryId) {
        if (selectedId != null) {
            return selectedId;
        }
        String searchText = lookupText(review.getGroupSuggestion(), review.getTechnologyStack(),
                review.getKnowledgePoint(), review.getQuestionTitle(), review.getQuestionContent());
        List<QuestionGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<QuestionGroup>()
                .eq(QuestionGroup::getStatus, CommonConstants.YES));
        for (QuestionGroup group : groups) {
            if (containsNormalized(searchText, group.getGroupName())
                    || containsNormalized(searchText, group.getCanonicalTitle())
                    || containsNormalized(searchText, group.getMainKnowledgePoint())) {
                return group.getId();
            }
        }
        for (String candidateName : groupCandidates(searchText)) {
            Long id = findGroupId(groups, candidateName);
            if (id != null) {
                return id;
            }
        }
        if (categoryId != null) {
            return groups.stream()
                    .filter(group -> categoryId.equals(group.getCategoryId()))
                    .map(QuestionGroup::getId)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private List<Long> resolveTagIds(QuestionReview review) {
        String searchText = lookupText(review.getTagSuggestionsJson(), review.getTechnologyStack(),
                review.getKnowledgePoint(), review.getQuestionTitle(), review.getQuestionContent());
        List<String> names = new ArrayList<>(parseStringList(review.getTagSuggestionsJson()));
        names.addAll(tagCandidates(searchText));
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        List<QuestionTag> tags = tagMapper.selectList(new LambdaQueryWrapper<QuestionTag>()
                .eq(QuestionTag::getStatus, CommonConstants.YES));
        List<Long> ids = new ArrayList<>();
        for (String name : names) {
            for (QuestionTag tag : tags) {
                if (containsNormalized(name, tag.getTagName()) || containsNormalized(searchText, tag.getTagName())) {
                    ids.add(tag.getId());
                    break;
                }
            }
        }
        return ids.stream().filter(id -> id != null).distinct().toList();
    }

    private Long findCategoryId(List<QuestionCategory> categories, String candidateName) {
        return categories.stream()
                .filter(category -> normalizedEquals(category.getCategoryName(), candidateName))
                .map(QuestionCategory::getId)
                .findFirst()
                .orElse(null);
    }

    private Long findGroupId(List<QuestionGroup> groups, String candidateName) {
        return groups.stream()
                .filter(group -> normalizedEquals(group.getGroupName(), candidateName)
                        || normalizedEquals(group.getCanonicalTitle(), candidateName)
                        || normalizedEquals(group.getMainKnowledgePoint(), candidateName))
                .map(QuestionGroup::getId)
                .findFirst()
                .orElse(null);
    }

    private List<String> categoryCandidates(String text) {
        List<String> names = new ArrayList<>();
        if (containsAny(text, "mysql", "sql", "索引", "事务", "explain")) {
            names.addAll(List.of("MySQL", "MySQL 性能优化"));
        }
        if (containsAny(text, "redis", "缓存", "cache", "分布式锁")) {
            names.addAll(List.of("Redis", "Redis 缓存与锁"));
        }
        if (containsAny(text, "jvm", "gc", "垃圾回收", "内存")) {
            names.add("JVM");
        }
        if (containsAny(text, "并发", "多线程", "线程", "thread", "concurrency", "juc", "锁")) {
            names.addAll(List.of("并发", "Concurrency"));
        }
        if (containsAny(text, "collection", "collections", "集合", "hashmap", "map", "list", "set")) {
            names.addAll(List.of("集合", "Collections", "Java基础", "Java Basics"));
        }
        if (containsAny(text, "spring", "springboot", "spring boot")) {
            names.addAll(List.of("Spring Boot", "Spring Boot 实战"));
        }
        if (containsAny(text, "微服务", "microservice", "gateway", "网关")) {
            names.addAll(List.of("微服务", "Microservices", "微服务与网关"));
        }
        if (containsAny(text, "设计模式", "designpattern", "pattern")) {
            names.addAll(List.of("设计模式", "Design Patterns"));
        }
        if (containsAny(text, "项目", "场景", "scenario")) {
            names.addAll(List.of("项目场景", "Project Scenario", "项目场景表达"));
        }
        names.addAll(List.of("Java基础", "Java Basics"));
        return names;
    }

    private List<String> groupCandidates(String text) {
        List<String> names = new ArrayList<>();
        if (containsAny(text, "hashmap")) {
            names.add("HashMap");
        }
        if (containsAny(text, "jvm", "gc", "垃圾回收")) {
            names.add("JVM GC");
        }
        if (containsAny(text, "线程池", "threadpool", "thread pool")) {
            names.addAll(List.of("线程池", "ThreadPool"));
        }
        if (containsAny(text, "多线程", "并发", "juc", "线程")) {
            names.addAll(List.of("多线程基础", "并发基础", "线程池", "ThreadPool"));
        }
        if (containsAny(text, "mysql", "索引", "explain")) {
            names.addAll(List.of("MySQL索引", "MySQL Index"));
        }
        if (containsAny(text, "redis", "缓存一致", "cache consistency")) {
            names.addAll(List.of("Redis缓存一致性", "Redis Cache Consistency"));
        }
        return names;
    }

    private List<String> tagCandidates(String text) {
        List<String> names = new ArrayList<>();
        if (containsAny(text, "hashmap")) {
            names.add("HashMap");
        }
        if (containsAny(text, "jvm", "gc")) {
            names.add("JVM");
        }
        if (containsAny(text, "多线程", "并发", "线程池", "threadpool", "thread")) {
            names.addAll(List.of("线程池", "ThreadPool"));
        }
        if (containsAny(text, "mysql", "索引", "explain")) {
            names.addAll(List.of("MySQL索引", "MySQL Index"));
        }
        if (containsAny(text, "redis", "缓存")) {
            names.addAll(List.of("Redis缓存", "Redis Cache"));
        }
        if (containsAny(text, "spring")) {
            names.addAll(List.of("Spring事务", "Spring Transaction"));
        }
        return names;
    }

    private boolean containsAny(String text, String... keywords) {
        String normalizedText = normalizeLookup(text);
        for (String keyword : keywords) {
            if (normalizedText.contains(normalizeLookup(keyword))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNormalized(String text, String value) {
        String normalizedText = normalizeLookup(text);
        String normalizedValue = normalizeLookup(value);
        return StringUtils.hasText(normalizedText)
                && StringUtils.hasText(normalizedValue)
                && (normalizedText.contains(normalizedValue) || normalizedValue.contains(normalizedText));
    }

    private boolean normalizedEquals(String left, String right) {
        String normalizedLeft = normalizeLookup(left);
        String normalizedRight = normalizeLookup(right);
        return StringUtils.hasText(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private String lookupText(String... values) {
        return String.join(" ", java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .toList());
    }

    private String normalizeLookup(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .replace("/", "");
    }

    private boolean hasEditedFields(QuestionReviewApproveDTO dto) {
        return dto != null && (StringUtils.hasText(dto.getTitle())
                || StringUtils.hasText(dto.getContent())
                || StringUtils.hasText(dto.getReferenceAnswer())
                || StringUtils.hasText(dto.getAnalysis())
                || StringUtils.hasText(dto.getDifficulty())
                || StringUtils.hasText(dto.getQuestionType())
                || dto.getCategoryId() != null
                || dto.getGroupId() != null
                || dto.getTagIds() != null
                || dto.getStatus() != null
                || dto.getIsHighFrequency() != null
                || StringUtils.hasText(dto.getExperienceLevel()));
    }

    private void validateQuestionRefs(Long categoryId, Long groupId, List<Long> tagIds) {
        if (categoryId != null) {
            QuestionCategory category = categoryMapper.selectById(categoryId);
            if (category == null || !CommonConstants.YES.equals(category.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Question category unavailable");
            }
        }
        if (groupId != null) {
            QuestionGroup group = groupMapper.selectById(groupId);
            if (group == null || !CommonConstants.YES.equals(group.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Question group unavailable");
            }
        }
        if (tagIds != null) {
            for (Long tagId : tagIds.stream().filter(tagId -> tagId != null).distinct().toList()) {
                QuestionTag tag = tagMapper.selectById(tagId);
                if (tag == null || !CommonConstants.YES.equals(tag.getStatus())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "Question tag unavailable");
                }
            }
        }
    }

    private void applyQuestionFingerprints(Question question) {
        String normalizedTitle = QuestionTextNormalizeUtils.normalizeTitle(question.getTitle());
        String normalizedContent = QuestionTextNormalizeUtils.normalizeContent(
                question.getTitle(), question.getContent(), question.getReferenceAnswer(), question.getAnalysis());
        question.setNormalizedTitle(normalizedTitle);
        question.setNormalizedTitleHash(QuestionTextNormalizeUtils.sha256Hex(normalizedTitle));
        question.setContentHash(QuestionTextNormalizeUtils.sha256Hex(normalizedContent));
    }
    private void insertTagRelations(Long questionId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds.stream().filter(tagId -> tagId != null).distinct().toList()) {
            QuestionTagRelation relation = new QuestionTagRelation();
            relation.setQuestionId(questionId);
            relation.setTagId(tagId);
            tagRelationMapper.insert(relation);
        }
    }

    private QuestionReviewListVO toListVO(QuestionReview review) {
        QuestionReviewListVO vo = new QuestionReviewListVO();
        vo.setId(review.getId());
        vo.setBatchId(review.getBatchId());
        vo.setReviewStatus(review.getReviewStatus());
        vo.setAiCallLogId(review.getAiCallLogId());
        vo.setTargetPosition(review.getTargetPosition());
        vo.setTechnologyStack(review.getTechnologyStack());
        vo.setKnowledgePoint(review.getKnowledgePoint());
        vo.setQuestionType(review.getQuestionType());
        vo.setDifficulty(review.getDifficulty());
        vo.setExperienceYears(review.getExperienceYears());
        vo.setQuestionTitle(review.getQuestionTitle());
        vo.setCategoryId(review.getCategoryId());
        vo.setGroupId(review.getGroupId());
        vo.setApprovedQuestionId(review.getApprovedQuestionId());
        vo.setReviewerId(review.getReviewerId());
        vo.setReviewedAt(review.getReviewedAt());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }

    private QuestionReviewDetailVO toDetailVO(QuestionReview review) {
        QuestionReviewDetailVO vo = new QuestionReviewDetailVO();
        vo.setId(review.getId());
        vo.setBatchId(review.getBatchId());
        vo.setCreatedBy(review.getCreatedBy());
        vo.setReviewStatus(review.getReviewStatus());
        vo.setAiCallLogId(review.getAiCallLogId());
        vo.setTargetPosition(review.getTargetPosition());
        vo.setTechnologyStack(review.getTechnologyStack());
        vo.setKnowledgePoint(review.getKnowledgePoint());
        vo.setQuestionType(review.getQuestionType());
        vo.setDifficulty(review.getDifficulty());
        vo.setExperienceYears(review.getExperienceYears());
        vo.setRawAiResultJson(review.getRawAiResultJson());
        vo.setQuestionTitle(review.getQuestionTitle());
        vo.setQuestionContent(review.getQuestionContent());
        vo.setReferenceAnswer(review.getReferenceAnswer());
        vo.setAnalysis(review.getAnalysis());
        vo.setFollowUpQuestionsJson(review.getFollowUpQuestionsJson());
        vo.setTagSuggestionsJson(review.getTagSuggestionsJson());
        vo.setCategorySuggestion(review.getCategorySuggestion());
        vo.setGroupSuggestion(review.getGroupSuggestion());
        vo.setCategoryId(review.getCategoryId());
        vo.setGroupId(review.getGroupId());
        vo.setTagIdsJson(review.getTagIdsJson());
        vo.setEditedContentJson(review.getEditedContentJson());
        vo.setRejectReason(review.getRejectReason());
        vo.setApprovedQuestionId(review.getApprovedQuestionId());
        vo.setReviewerId(review.getReviewerId());
        vo.setReviewedAt(review.getReviewedAt());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setUpdatedAt(review.getUpdatedAt());
        return vo;
    }

    private List<Long> parseLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            }).stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String defaultText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo <= 0 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize <= 0 ? 10L : Math.min(pageSize, 100L);
    }

    private record ApprovedQuestionPayload(String title, String content, String referenceAnswer, String analysis,
                                           String difficulty, String questionType, Long categoryId, Long groupId,
                                           List<Long> tagIds, Integer status, Integer isHighFrequency,
                                           String experienceLevel) {

        private Map<String, Object> toMap(String editedReason) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("title", title);
            values.put("content", content);
            values.put("referenceAnswer", referenceAnswer);
            values.put("analysis", analysis);
            values.put("difficulty", difficulty);
            values.put("questionType", questionType);
            values.put("categoryId", categoryId);
            values.put("groupId", groupId);
            values.put("tagIds", tagIds);
            values.put("status", status);
            values.put("isHighFrequency", isHighFrequency);
            values.put("experienceLevel", experienceLevel);
            values.put("editedReason", editedReason);
            return values;
        }
    }
}
