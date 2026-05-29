package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.config.QuestionDuplicateProperties;
import com.codecoachai.question.domain.dto.BatchQuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.BatchQuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateCheckDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvaluationDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionRelationCreateDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionDuplicateReview;
import com.codecoachai.question.domain.entity.QuestionGroup;
import com.codecoachai.question.domain.entity.QuestionRelation;
import com.codecoachai.question.domain.entity.QuestionTagRelation;
import com.codecoachai.question.domain.enums.QuestionDuplicateMatchType;
import com.codecoachai.question.domain.enums.QuestionDuplicateReviewStatus;
import com.codecoachai.question.domain.enums.QuestionRelationStatus;
import com.codecoachai.question.domain.enums.QuestionRelationType;
import com.codecoachai.question.domain.vo.QuestionDuplicateCheckResultVO;
import com.codecoachai.question.domain.vo.BatchQuestionDuplicateResultVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvaluationVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateFeedbackStatsVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO;
import com.codecoachai.question.domain.vo.QuestionRelationVO;
import com.codecoachai.question.domain.vo.QuestionSummaryVO;
import com.codecoachai.question.mapper.QuestionDuplicateReviewMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRelationMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionDuplicateServiceImpl implements QuestionDuplicateService {

    private static final TypeReference<List<Map<String, Object>>> SCORE_DETAIL_TYPE = new TypeReference<>() {
    };
    private static final List<ScoreBucketRange> FEEDBACK_SCORE_BUCKETS = List.of(
            new ScoreBucketRange("90-100", BigDecimal.valueOf(90), BigDecimal.valueOf(100)),
            new ScoreBucketRange("80-89", BigDecimal.valueOf(80), BigDecimal.valueOf(90)),
            new ScoreBucketRange("70-79", BigDecimal.valueOf(70), BigDecimal.valueOf(80)),
            new ScoreBucketRange("0-69", BigDecimal.ZERO, BigDecimal.valueOf(70))
    );

    private final QuestionMapper questionMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionDuplicateReviewMapper duplicateReviewMapper;
    private final QuestionRelationMapper relationMapper;
    private final QuestionTagRelationMapper tagRelationMapper;
    private final QuestionEmbeddingIndexService questionEmbeddingIndexService;
    private final QuestionDuplicateProperties duplicateProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDuplicateCheckResultVO checkDuplicate(QuestionDuplicateCheckDTO dto) {
        Long operatorId = SecurityAssert.requireLoginUserId();
        List<Long> questionIds = normalizeQuestionIds(dto);
        if (questionIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "questionId or questionIds is required");
        }
        if (questionIds.size() > duplicateProperties.getMaxBatchCheckCount()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "question duplicate check limit is " + duplicateProperties.getMaxBatchCheckCount());
        }
        return checkDuplicate(questionIds, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDuplicateCheckResultVO checkDuplicateForQuestion(Long questionId, Long operatorId) {
        if (questionId == null) {
            return emptyResult();
        }
        try {
            return checkDuplicate(List.of(questionId), operatorId);
        } catch (Exception ignored) {
            return emptyResult();
        }
    }

    @Override
    public PageResult<QuestionDuplicateReviewListVO> pageReviews(QuestionDuplicateReviewQueryDTO query) {
        QuestionDuplicateReviewQueryDTO safeQuery = query == null ? new QuestionDuplicateReviewQueryDTO() : query;
        Page<QuestionDuplicateReview> page = duplicateReviewMapper.selectPage(
                Page.of(defaultPage(safeQuery.getPageNo()), defaultSize(safeQuery.getPageSize())),
                buildReviewWrapper(safeQuery));
        List<QuestionDuplicateReview> records = page.getRecords();
        Map<Long, Question> questionMap = loadQuestionMap(records);
        return PageResult.of(records.stream()
                        .map(review -> toListVO(review, questionMap.get(review.getSourceQuestionId()),
                                questionMap.get(review.getTargetQuestionId())))
                        .toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QuestionDuplicateFeedbackStatsVO feedbackStats() {
        List<QuestionDuplicateReview> reviews = duplicateReviewMapper.selectList(new LambdaQueryWrapper<QuestionDuplicateReview>()
                .orderByDesc(QuestionDuplicateReview::getCreatedAt));
        QuestionDuplicateFeedbackStatsVO vo = new QuestionDuplicateFeedbackStatsVO();
        long total = reviews.size();
        vo.setTotalCount(total);
        Map<String, Long> statusCounts = countBy(reviews, QuestionDuplicateReview::getReviewStatus);
        Map<String, Long> matchTypeCounts = countBy(reviews, QuestionDuplicateReview::getMatchType);
        Map<String, Long> scoreBandCounts = countBy(reviews, review -> firstText(review.getScoreBand(),
                parseReasonToken(review.getMatchReason(), "scoreBand")));
        long pending = statusCounts.getOrDefault(QuestionDuplicateReviewStatus.PENDING.name(), 0L);
        long confirmed = statusCounts.getOrDefault(QuestionDuplicateReviewStatus.CONFIRMED.name(), 0L);
        long ignored = statusCounts.getOrDefault(QuestionDuplicateReviewStatus.IGNORED.name(), 0L);
        long resolved = confirmed + ignored;
        vo.setPendingCount(pending);
        vo.setConfirmedCount(confirmed);
        vo.setIgnoredCount(ignored);
        vo.setResolvedCount(resolved);
        vo.setConfirmationRate(rate(confirmed, resolved));
        vo.setIgnoreRate(rate(ignored, resolved));
        vo.setSampleCoverageRate(rate(resolved, total));
        vo.setAverageSimilarityScore(averageScore(reviews));
        vo.setStatusCounts(statusCounts);
        vo.setMatchTypeCounts(matchTypeCounts);
        vo.setScoreBandCounts(scoreBandCounts);
        List<QuestionDuplicateFeedbackStatsVO.Bucket> buckets = FEEDBACK_SCORE_BUCKETS.stream()
                .map(range -> toFeedbackBucket(range, reviews))
                .toList();
        vo.setScoreBuckets(buckets);
        vo.setThresholdRecommendation(buildFeedbackRecommendation(vo, buckets));
        vo.setWarningItems(buildFeedbackWarnings(vo, buckets));
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public QuestionDuplicateEvaluationVO evaluate(QuestionDuplicateEvaluationDTO dto) {
        List<QuestionDuplicateEvaluationDTO.Sample> samples = dto == null || dto.getSamples() == null
                ? List.of()
                : dto.getSamples().stream()
                .filter(Objects::nonNull)
                .limit(100)
                .toList();
        QuestionDuplicateEvaluationVO vo = new QuestionDuplicateEvaluationVO();
        vo.setSampleCount(samples.size());
        vo.setGeneratedAt(LocalDateTime.now());
        if (samples.isEmpty()) {
            vo.setEvaluatedCount(0);
            vo.setPassedCount(0);
            vo.setFailedCount(0);
            vo.setMissingQuestionCount(0);
            vo.setAccuracyRate(rate(0, 0));
            vo.setItems(List.of());
            return vo;
        }

        List<QuestionDuplicateEvaluationVO.Item> items = new ArrayList<>();
        int evaluated = 0;
        int passed = 0;
        int missing = 0;
        for (QuestionDuplicateEvaluationDTO.Sample sample : samples) {
            QuestionDuplicateEvaluationVO.Item item = evaluateSample(sample);
            items.add(item);
            if (item.getPredicted() == null) {
                missing++;
                continue;
            }
            evaluated++;
            if (Boolean.TRUE.equals(item.getPassed())) {
                passed++;
            }
        }
        vo.setEvaluatedCount(evaluated);
        vo.setPassedCount(passed);
        vo.setFailedCount(Math.max(evaluated - passed, 0));
        vo.setMissingQuestionCount(missing);
        vo.setAccuracyRate(rate(passed, evaluated));
        vo.setItems(items);
        return vo;
    }

    @Override
    public QuestionDuplicateReviewDetailVO getReview(Long id) {
        return toDetailVO(getReviewOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDuplicateReviewDetailVO merge(Long id, QuestionDuplicateMergeDTO dto) {
        QuestionDuplicateReview review = getReviewOrThrow(id);
        if (QuestionDuplicateReviewStatus.CONFIRMED.name().equals(review.getReviewStatus())) {
            return toDetailVO(review);
        }
        if (!QuestionDuplicateReviewStatus.PENDING.name().equals(review.getReviewStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Duplicate review cannot be confirmed");
        }
        QuestionRelationType relationType = parseRelationType(dto == null ? null : dto.getRelationType(),
                QuestionRelationType.SAME_INTENT);
        Long reviewerId = SecurityAssert.requireLoginUserId();
        QuestionRelation relation = createOrGetRelation(review.getSourceQuestionId(), review.getTargetQuestionId(),
                relationType, dto == null ? null : dto.getReason(), review.getSimilarityScore(), reviewerId);
        if (QuestionRelationType.SAME_INTENT.equals(relationType)) {
            ensureCanonicalGroup(review.getSourceQuestionId(), review.getTargetQuestionId());
        }
        review.setReviewStatus(QuestionDuplicateReviewStatus.CONFIRMED.name());
        review.setRelationId(relation.getId());
        review.setReviewedBy(reviewerId);
        review.setReviewedAt(LocalDateTime.now());
        duplicateReviewMapper.updateById(review);
        return toDetailVO(getReviewOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDuplicateReviewDetailVO ignore(Long id, QuestionDuplicateIgnoreDTO dto) {
        QuestionDuplicateReview review = getReviewOrThrow(id);
        if (QuestionDuplicateReviewStatus.IGNORED.name().equals(review.getReviewStatus())) {
            return toDetailVO(review);
        }
        if (!QuestionDuplicateReviewStatus.PENDING.name().equals(review.getReviewStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Confirmed duplicate review cannot be ignored");
        }
        review.setReviewStatus(QuestionDuplicateReviewStatus.IGNORED.name());
        review.setIgnoredReason(dto == null ? null : dto.getIgnoredReason());
        review.setReviewedBy(SecurityAssert.requireLoginUserId());
        review.setReviewedAt(LocalDateTime.now());
        duplicateReviewMapper.updateById(review);
        return toDetailVO(getReviewOrThrow(id));
    }

    @Override
    public BatchQuestionDuplicateResultVO batchMerge(BatchQuestionDuplicateMergeDTO dto) {
        List<Long> ids = normalizeReviewIds(dto == null ? null : dto.getIds());
        BatchQuestionDuplicateResultVO result = new BatchQuestionDuplicateResultVO();
        result.setRequestedCount(ids.size());
        if (ids.isEmpty()) {
            return result;
        }
        validateBatchReviewCount(ids);
        QuestionDuplicateMergeDTO mergeDTO = new QuestionDuplicateMergeDTO();
        mergeDTO.setRelationType(dto.getRelationType());
        mergeDTO.setReason(dto.getReason());
        int success = 0;
        for (Long id : ids) {
            try {
                transactionTemplate.executeWithoutResult(status -> merge(id, mergeDTO));
                success++;
            } catch (Exception ex) {
                result.getFailures().add(new BatchQuestionDuplicateResultVO.Failure(id,
                        firstText(ex.getMessage(), "merge failed")));
            }
        }
        result.setSuccessCount(success);
        result.setFailureCount(result.getFailures().size());
        return result;
    }

    @Override
    public BatchQuestionDuplicateResultVO batchIgnore(BatchQuestionDuplicateIgnoreDTO dto) {
        List<Long> ids = normalizeReviewIds(dto == null ? null : dto.getIds());
        BatchQuestionDuplicateResultVO result = new BatchQuestionDuplicateResultVO();
        result.setRequestedCount(ids.size());
        if (ids.isEmpty()) {
            return result;
        }
        validateBatchReviewCount(ids);
        QuestionDuplicateIgnoreDTO ignoreDTO = new QuestionDuplicateIgnoreDTO();
        ignoreDTO.setIgnoredReason(dto.getIgnoredReason());
        int success = 0;
        for (Long id : ids) {
            try {
                transactionTemplate.executeWithoutResult(status -> ignore(id, ignoreDTO));
                success++;
            } catch (Exception ex) {
                result.getFailures().add(new BatchQuestionDuplicateResultVO.Failure(id,
                        firstText(ex.getMessage(), "ignore failed")));
            }
        }
        result.setSuccessCount(success);
        result.setFailureCount(result.getFailures().size());
        return result;
    }

    private List<Long> normalizeReviewIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void validateBatchReviewCount(List<Long> ids) {
        int limit = Math.max(1, duplicateProperties.getMaxBatchCheckCount());
        if (ids.size() > limit) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question duplicate batch operation limit is " + limit);
        }
    }

    @Override
    public List<QuestionRelationVO> listRelations(Long questionId) {
        getQuestionOrThrow(questionId);
        return relationMapper.selectList(new LambdaQueryWrapper<QuestionRelation>()
                        .eq(QuestionRelation::getRelationStatus, QuestionRelationStatus.ACTIVE.name())
                        .and(wrapper -> wrapper.eq(QuestionRelation::getSourceQuestionId, questionId)
                                .or()
                                .eq(QuestionRelation::getTargetQuestionId, questionId))
                        .orderByDesc(QuestionRelation::getCreatedAt))
                .stream()
                .map(this::toRelationVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionRelationVO createRelation(Long questionId, QuestionRelationCreateDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "request body is required");
        }
        QuestionRelationType relationType = parseRelationType(dto.getRelationType(), QuestionRelationType.RELATED);
        QuestionRelation relation = createOrGetRelation(questionId, dto.getTargetQuestionId(),
                relationType, dto.getReason(), null, SecurityAssert.requireLoginUserId());
        return toRelationVO(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long questionId, Long relationId) {
        QuestionRelation relation = relationMapper.selectById(relationId);
        if (relation == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question relation not found");
        }
        if (!questionId.equals(relation.getSourceQuestionId()) && !questionId.equals(relation.getTargetQuestionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question relation does not belong to this question");
        }
        relation.setRelationStatus(QuestionRelationStatus.DELETED.name());
        relationMapper.updateById(relation);
        relationMapper.deleteById(relationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void invalidatePendingReviewsForQuestion(Long questionId, Long operatorId, String reason) {
        if (questionId == null) {
            return;
        }
        List<QuestionDuplicateReview> reviews = duplicateReviewMapper.selectList(new LambdaQueryWrapper<QuestionDuplicateReview>()
                .eq(QuestionDuplicateReview::getReviewStatus, QuestionDuplicateReviewStatus.PENDING.name())
                .and(wrapper -> wrapper.eq(QuestionDuplicateReview::getSourceQuestionId, questionId)
                        .or()
                        .eq(QuestionDuplicateReview::getTargetQuestionId, questionId)));
        if (reviews.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String ignoredReason = firstText(reason, "Question unavailable, duplicate review auto ignored");
        for (QuestionDuplicateReview review : reviews) {
            review.setReviewStatus(QuestionDuplicateReviewStatus.IGNORED.name());
            review.setIgnoredReason(ignoredReason);
            review.setReviewedBy(operatorId);
            review.setReviewedAt(now);
            duplicateReviewMapper.updateById(review);
        }
    }

    private QuestionDuplicateCheckResultVO checkDuplicate(List<Long> questionIds, Long operatorId) {
        List<Long> createdIds = new ArrayList<>();
        int checkedCount = 0;
        for (Long questionId : questionIds) {
            Question source = questionMapper.selectById(questionId);
            if (source == null) {
                continue;
            }
            checkedCount++;
            Set<String> handledPairs = new LinkedHashSet<>();
            for (Question target : findHardFingerprintCandidates(source)) {
                MatchResult match = hardFingerprintMatch(source, target);
                if (match == null || relationExists(source.getId(), target.getId())
                        || duplicateReviewExists(source.getId(), target.getId())) {
                    continue;
                }
                QuestionDuplicateReview review = buildReview(source, target, match, operatorId);
                duplicateReviewMapper.insert(review);
                createdIds.add(review.getId());
                handledPairs.add(pairKey(source.getId(), target.getId()));
            }
            List<Question> ruleCandidates = findTargetCandidates(source);
            for (Question target : ruleCandidates) {
                MatchResult match = match(source, target);
                if (match == null || relationExists(source.getId(), target.getId())
                        || duplicateReviewExists(source.getId(), target.getId())) {
                    continue;
                }
                QuestionDuplicateReview review = buildReview(source, target, match, operatorId);
                duplicateReviewMapper.insert(review);
                createdIds.add(review.getId());
                handledPairs.add(pairKey(source.getId(), target.getId()));
            }
            for (SemanticCandidate candidate : findSemanticCandidates(source, ruleCandidates)) {
                Question target = candidate.question();
                String pairKey = pairKey(source.getId(), target.getId());
                if (handledPairs.contains(pairKey) || relationExists(source.getId(), target.getId())
                        || duplicateReviewExists(source.getId(), target.getId())) {
                    continue;
                }
                MatchResult match = semanticMatch(source, target, candidate.score());
                if (match == null) {
                    continue;
                }
                QuestionDuplicateReview review = buildReview(source, target, match, operatorId);
                duplicateReviewMapper.insert(review);
                createdIds.add(review.getId());
                handledPairs.add(pairKey);
            }
        }
        QuestionDuplicateCheckResultVO vo = new QuestionDuplicateCheckResultVO();
        vo.setCheckedCount(checkedCount);
        vo.setCreatedCount(createdIds.size());
        vo.setReviewIds(createdIds);
        return vo;
    }

    private List<Long> normalizeQuestionIds(QuestionDuplicateCheckDTO dto) {
        if (dto == null) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (dto.getQuestionId() != null) {
            ids.add(dto.getQuestionId());
        }
        if (dto.getQuestionIds() != null) {
            dto.getQuestionIds().stream().filter(id -> id != null).forEach(ids::add);
        }
        return ids.stream().toList();
    }

    private List<Question> findHardFingerprintCandidates(Question source) {
        String titleHash = firstText(source.getNormalizedTitleHash(),
                QuestionTextNormalizeUtils.sha256Hex(QuestionTextNormalizeUtils.normalizeTitle(source.getTitle())));
        String contentHash = firstText(source.getContentHash(), QuestionTextNormalizeUtils.sha256Hex(
                QuestionTextNormalizeUtils.normalizeContent(source.getTitle(), source.getContent(),
                        source.getReferenceAnswer(), source.getAnalysis())));
        if (!StringUtils.hasText(titleHash) && !StringUtils.hasText(contentHash)) {
            return List.of();
        }
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .ne(Question::getId, source.getId())
                .eq(Question::getStatus, CommonConstants.YES)
                .and(query -> {
                    boolean hasTitleHash = StringUtils.hasText(titleHash);
                    boolean hasContentHash = StringUtils.hasText(contentHash);
                    if (hasTitleHash) {
                        query.eq(Question::getNormalizedTitleHash, titleHash);
                    }
                    if (hasTitleHash && hasContentHash) {
                        query.or();
                    }
                    if (hasContentHash) {
                        query.eq(Question::getContentHash, contentHash);
                    }
                })
                .orderByDesc(Question::getUpdatedAt)
                .last("limit " + duplicateProperties.getMaxRuleCandidateCount());
        return questionMapper.selectList(wrapper);
    }

    private MatchResult hardFingerprintMatch(Question source, Question target) {
        String sourceContentHash = firstText(source.getContentHash(), QuestionTextNormalizeUtils.sha256Hex(
                QuestionTextNormalizeUtils.normalizeContent(source.getTitle(), source.getContent(),
                        source.getReferenceAnswer(), source.getAnalysis())));
        String targetContentHash = firstText(target.getContentHash(), QuestionTextNormalizeUtils.sha256Hex(
                QuestionTextNormalizeUtils.normalizeContent(target.getTitle(), target.getContent(),
                        target.getReferenceAnswer(), target.getAnalysis())));
        if (StringUtils.hasText(sourceContentHash) && sourceContentHash.equals(targetContentHash)) {
            return hardMatch(QuestionDuplicateMatchType.HARD_CONTENT_HASH, score(100D), "content fingerprint exact match");
        }
        String sourceTitleHash = firstText(source.getNormalizedTitleHash(),
                QuestionTextNormalizeUtils.sha256Hex(QuestionTextNormalizeUtils.normalizeTitle(source.getTitle())));
        String targetTitleHash = firstText(target.getNormalizedTitleHash(),
                QuestionTextNormalizeUtils.sha256Hex(QuestionTextNormalizeUtils.normalizeTitle(target.getTitle())));
        if (StringUtils.hasText(sourceTitleHash) && sourceTitleHash.equals(targetTitleHash)) {
            return hardMatch(QuestionDuplicateMatchType.HARD_TITLE_HASH, score(98D), "normalized title fingerprint exact match");
        }
        return null;
    }

    private MatchResult hardMatch(QuestionDuplicateMatchType matchType, BigDecimal score, String reason) {
        return new MatchResult(matchType, score, reason + "; scoreBand=STRONG", "STRONG",
                writeScoreDetailJson(List.of(scorePartPayload(matchType.name(), "精确", score))));
    }

    private MatchResult ruleMatch(QuestionDuplicateMatchType matchType, BigDecimal score, String reason) {
        String scoreBand = score.compareTo(BigDecimal.valueOf(90)) >= 0 ? "STRONG" : "REVIEW";
        return new MatchResult(matchType, score, reason + "; scoreBand=" + scoreBand, scoreBand,
                writeScoreDetailJson(List.of(scorePartPayload(matchType.name(), "规则", score))));
    }

    private List<Question> findTargetCandidates(Question source) {
        int candidateLimit = duplicateProperties.isVectorFirstEnabled()
                ? duplicateProperties.getRuleFallbackCandidateCount()
                : duplicateProperties.getMaxRuleCandidateCount();
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .ne(Question::getId, source.getId())
                .eq(Question::getStatus, CommonConstants.YES)
                .eq(source.getCategoryId() != null, Question::getCategoryId, source.getCategoryId())
                .eq(StringUtils.hasText(source.getQuestionType()), Question::getQuestionType, source.getQuestionType())
                .eq(StringUtils.hasText(source.getDifficulty()), Question::getDifficulty, source.getDifficulty())
                .orderByDesc(Question::getUpdatedAt)
                .last("limit " + candidateLimit);
        return questionMapper.selectList(wrapper);
    }

    private QuestionDuplicateEvaluationVO.Item evaluateSample(QuestionDuplicateEvaluationDTO.Sample sample) {
        QuestionDuplicateEvaluationVO.Item item = new QuestionDuplicateEvaluationVO.Item();
        item.setCaseId(sample.getCaseId());
        item.setSourceQuestionId(sample.getSourceQuestionId());
        item.setTargetQuestionId(sample.getTargetQuestionId());
        item.setExpected(normalizeExpectedDuplicate(sample.getExpected()));
        item.setNote(sample.getNote());
        Question source = sample.getSourceQuestionId() == null ? null : questionMapper.selectById(sample.getSourceQuestionId());
        Question target = sample.getTargetQuestionId() == null ? null : questionMapper.selectById(sample.getTargetQuestionId());
        if (source == null || target == null) {
            item.setReason("source or target question not found");
            return item;
        }
        MatchResult match = evaluateQuestionPair(source, target);
        String predicted = predictedDuplicateLabel(match);
        item.setPredicted(predicted);
        item.setPassed(expectedMatches(item.getExpected(), predicted));
        if (match != null) {
            item.setMatchType(match.matchType().name());
            item.setScore(match.score());
            item.setScoreBand(match.scoreBand());
            item.setReason(match.reason());
            item.setScoreParts(parseScoreDetailJson(match.scoreDetailJson()));
        } else {
            item.setReason("no duplicate signal passed the current thresholds");
        }
        return item;
    }

    private MatchResult evaluateQuestionPair(Question source, Question target) {
        MatchResult hard = hardFingerprintMatch(source, target);
        if (hard != null) {
            return hard;
        }
        MatchResult rule = match(source, target);
        if (rule != null) {
            return rule;
        }
        Double vectorScore = semanticVectorScore(source, target);
        if (vectorScore != null) {
            return semanticMatch(source, target, vectorScore);
        }
        return null;
    }

    private Double semanticVectorScore(Question source, Question target) {
        if (source == null || target == null || source.getId() == null || target.getId() == null) {
            return null;
        }
        try {
            return questionEmbeddingIndexService.searchSimilarIndexed(
                            source.getId(),
                            duplicateProperties.getVectorSearchLimit(),
                            duplicateProperties.effectiveSemanticReviewThreshold())
                    .stream()
                    .filter(hit -> Objects.equals(hit.questionId(), target.getId()))
                    .map(QuestionEmbeddingIndexService.SemanticHit::score)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Question duplicate evaluation vector search failed sourceId={} targetId={}",
                    source.getId(), target.getId(), ex);
            return null;
        }
    }

    private String normalizeExpectedDuplicate(String expected) {
        if (!StringUtils.hasText(expected)) {
            return "NOT_DUPLICATE";
        }
        String value = expected.trim().toUpperCase().replace('-', '_');
        if ("DUPLICATE".equals(value) || "REVIEW".equals(value) || "NOT_DUPLICATE".equals(value)) {
            return value;
        }
        if ("TRUE".equals(value) || "YES".equals(value)) {
            return "DUPLICATE";
        }
        if ("FALSE".equals(value) || "NO".equals(value)) {
            return "NOT_DUPLICATE";
        }
        return value;
    }

    private String predictedDuplicateLabel(MatchResult match) {
        if (match == null) {
            return "NOT_DUPLICATE";
        }
        if ("STRONG".equals(match.scoreBand())) {
            return "DUPLICATE";
        }
        return "REVIEW";
    }

    private boolean expectedMatches(String expected, String predicted) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(predicted)) {
            return false;
        }
        if (expected.equals(predicted)) {
            return true;
        }
        return "DUPLICATE".equals(expected) && "REVIEW".equals(predicted);
    }

    private MatchResult match(Question source, Question target) {
        String sourceTitle = source.getTitle();
        String targetTitle = target.getTitle();
        if (StringUtils.hasText(sourceTitle) && sourceTitle.trim().equals(targetTitle == null ? null : targetTitle.trim())) {
            return ruleMatch(QuestionDuplicateMatchType.TITLE_EXACT, score(100D), "title exact match");
        }
        String normalizedSourceTitle = QuestionTextNormalizeUtils.normalizeTitle(sourceTitle);
        String normalizedTargetTitle = QuestionTextNormalizeUtils.normalizeTitle(targetTitle);
        if (StringUtils.hasText(normalizedSourceTitle) && normalizedSourceTitle.equals(normalizedTargetTitle)) {
            return ruleMatch(QuestionDuplicateMatchType.TITLE_NORMALIZED_EQUAL, score(95D),
                    "normalized title match");
        }
        double titleJaccard = QuestionTextNormalizeUtils.jaccard(sourceTitle, targetTitle);
        double titleLevenshtein = QuestionTextNormalizeUtils.levenshteinSimilarity(sourceTitle, targetTitle);
        double titleSimilarity = Math.max(titleJaccard, titleLevenshtein);
        if (titleJaccard >= duplicateProperties.getTitleJaccardThreshold()
                || titleLevenshtein >= duplicateProperties.getTitleLevenshteinThreshold()) {
            return ruleMatch(QuestionDuplicateMatchType.TITLE_SIMILAR, score(titleSimilarity * 100D),
                    "title similarity match");
        }
        double contentSimilarity = QuestionTextNormalizeUtils.jaccard(
                QuestionTextNormalizeUtils.snapshot(source.getContent(), 500),
                QuestionTextNormalizeUtils.snapshot(target.getContent(), 500));
        if (contentSimilarity >= duplicateProperties.getContentSimilarityThreshold()) {
            return ruleMatch(QuestionDuplicateMatchType.CONTENT_SIMILAR, score(contentSimilarity * 100D),
                    "content similarity match");
        }
        return null;
    }

    private MatchResult semanticMatch(Question source, Question target, double vectorScore) {
        double reviewThreshold = duplicateProperties.effectiveSemanticReviewThreshold();
        double strongThreshold = duplicateProperties.effectiveSemanticStrongThreshold();
        if (vectorScore < reviewThreshold) {
            return null;
        }
        double titleSimilarity = Math.max(
                QuestionTextNormalizeUtils.jaccard(source.getTitle(), target.getTitle()),
                QuestionTextNormalizeUtils.levenshteinSimilarity(source.getTitle(), target.getTitle()));
        double contentSimilarity = QuestionTextNormalizeUtils.jaccard(
                QuestionTextNormalizeUtils.snapshot(source.getContent(), 500),
                QuestionTextNormalizeUtils.snapshot(target.getContent(), 500));
        double textScore = Math.max(titleSimilarity, contentSimilarity);
        double metadataScore = metadataSimilarityScore(source, target);
        double tagScore = tagSimilarityScore(source.getId(), target.getId());
        double finalScore = weightedSemanticScore(vectorScore, textScore, metadataScore, tagScore);
        if (finalScore < reviewThreshold) {
            return null;
        }
        BigDecimal vectorPercent = score(vectorScore * 100D);
        BigDecimal textPercent = score(textScore * 100D);
        BigDecimal metadataPercent = score(metadataScore * 100D);
        BigDecimal tagPercent = score(tagScore * 100D);
        BigDecimal finalPercent = score(finalScore * 100D);
        String scoreBand = finalScore >= strongThreshold ? "STRONG" : "REVIEW";
        String reason = "semantic vector match; vectorScore=" + vectorPercent
                + "; textScore=" + textPercent
                + "; metadataScore=" + metadataPercent
                + "; tagScore=" + tagPercent
                + "; finalScore=" + finalPercent
                + "; scoreBand=" + scoreBand
                + "; reviewThreshold=" + score(reviewThreshold * 100D)
                + "; strongThreshold=" + score(strongThreshold * 100D);
        String scoreDetailJson = writeScoreDetailJson(List.of(
                scorePartPayload("vectorScore", "向量", vectorPercent),
                scorePartPayload("textScore", "文本", textPercent),
                scorePartPayload("metadataScore", "元数据", metadataPercent),
                scorePartPayload("tagScore", "标签", tagPercent),
                scorePartPayload("finalScore", "综合", finalPercent)
        ));
        return new MatchResult(QuestionDuplicateMatchType.SEMANTIC_SIMILAR, finalPercent, reason,
                scoreBand, scoreDetailJson);
    }

    private List<SemanticCandidate> findSemanticCandidates(Question source, List<Question> ruleCandidates) {
        try {
            List<QuestionEmbeddingIndexService.SemanticHit> hits = questionEmbeddingIndexService.searchSimilarIndexed(
                    source.getId(),
                    duplicateProperties.getVectorSearchLimit(),
                    duplicateProperties.effectiveSemanticReviewThreshold());
            if (hits.isEmpty()) {
                return List.of();
            }
            List<Long> hitQuestionIds = hits.stream()
                    .map(QuestionEmbeddingIndexService.SemanticHit::questionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, Double> scoreMap = new LinkedHashMap<>();
            for (QuestionEmbeddingIndexService.SemanticHit hit : hits) {
                scoreMap.putIfAbsent(hit.questionId(), hit.score());
            }
            if (hitQuestionIds.isEmpty()) {
                return List.of();
            }
            Map<Long, Question> questionMap = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                            .in(Question::getId, hitQuestionIds)
                            .eq(Question::getStatus, CommonConstants.YES))
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(Question::getId, item -> item));
            return hitQuestionIds.stream()
                    .map(questionMap::get)
                    .filter(Objects::nonNull)
                    .map(question -> new SemanticCandidate(question, scoreMap.getOrDefault(question.getId(), 0D)))
                    .sorted(Comparator.comparing(SemanticCandidate::score).reversed())
                    .toList();
        } catch (Exception ex) {
            log.warn("Question semantic duplicate candidate search failed questionId={}", source.getId(), ex);
            return List.of();
        }
    }

    private QuestionDuplicateReview buildReview(Question left, Question right, MatchResult match, Long operatorId) {
        QuestionPair pair = sortedPair(left, right);
        QuestionDuplicateReview review = new QuestionDuplicateReview();
        review.setSourceQuestionId(pair.source().getId());
        review.setTargetQuestionId(pair.target().getId());
        review.setReviewStatus(QuestionDuplicateReviewStatus.PENDING.name());
        review.setMatchType(match.matchType().name());
        review.setSimilarityScore(match.score());
        review.setMatchReason(match.reason());
        review.setScoreBand(match.scoreBand());
        review.setScoreDetailJson(match.scoreDetailJson());
        review.setSourceTitleSnapshot(QuestionTextNormalizeUtils.snapshot(pair.source().getTitle(), 255));
        review.setTargetTitleSnapshot(QuestionTextNormalizeUtils.snapshot(pair.target().getTitle(), 255));
        review.setSourceContentSnapshot(QuestionTextNormalizeUtils.snapshot(pair.source().getContent(), 500));
        review.setTargetContentSnapshot(QuestionTextNormalizeUtils.snapshot(pair.target().getContent(), 500));
        review.setSourceGroupId(pair.source().getGroupId());
        review.setTargetGroupId(pair.target().getGroupId());
        review.setCreatedBy(operatorId);
        return review;
    }

    private LambdaQueryWrapper<QuestionDuplicateReview> buildReviewWrapper(QuestionDuplicateReviewQueryDTO query) {
        String status = StringUtils.hasText(query.getReviewStatus())
                ? query.getReviewStatus()
                : QuestionDuplicateReviewStatus.PENDING.name();
        return new LambdaQueryWrapper<QuestionDuplicateReview>()
                .eq(QuestionDuplicateReview::getReviewStatus, status)
                .eq(StringUtils.hasText(query.getMatchType()), QuestionDuplicateReview::getMatchType, query.getMatchType())
                .eq(StringUtils.hasText(query.getScoreBand()), QuestionDuplicateReview::getScoreBand, query.getScoreBand())
                .and(query.getQuestionId() != null, wrapper -> wrapper
                        .eq(QuestionDuplicateReview::getSourceQuestionId, query.getQuestionId())
                        .or()
                        .eq(QuestionDuplicateReview::getTargetQuestionId, query.getQuestionId()))
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(QuestionDuplicateReview::getSourceTitleSnapshot, query.getKeyword())
                        .or()
                        .like(QuestionDuplicateReview::getTargetTitleSnapshot, query.getKeyword())
                        .or()
                        .like(QuestionDuplicateReview::getMatchReason, query.getKeyword()))
                .orderByDesc(QuestionDuplicateReview::getCreatedAt);
    }

    private QuestionRelation createOrGetRelation(Long leftQuestionId, Long rightQuestionId,
                                                 QuestionRelationType relationType, String reason,
                                                 BigDecimal similarityScore, Long operatorId) {
        Question left = getQuestionOrThrow(leftQuestionId);
        Question right = getQuestionOrThrow(rightQuestionId);
        if (left.getId().equals(right.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cannot create relation with the same question");
        }
        QuestionPair pair = sortedPair(left, right);
        QuestionRelation existing = relationMapper.selectOne(new LambdaQueryWrapper<QuestionRelation>()
                .eq(QuestionRelation::getSourceQuestionId, pair.source().getId())
                .eq(QuestionRelation::getTargetQuestionId, pair.target().getId())
                .eq(QuestionRelation::getRelationType, relationType.name())
                .eq(QuestionRelation::getRelationStatus, QuestionRelationStatus.ACTIVE.name())
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        QuestionRelation relation = new QuestionRelation();
        relation.setSourceQuestionId(pair.source().getId());
        relation.setTargetQuestionId(pair.target().getId());
        relation.setRelationType(relationType.name());
        relation.setRelationStatus(QuestionRelationStatus.ACTIVE.name());
        relation.setReason(reason);
        relation.setSimilarityScore(similarityScore);
        relation.setCreatedBy(operatorId);
        relationMapper.insert(relation);
        return relation;
    }

    private void ensureCanonicalGroup(Long leftQuestionId, Long rightQuestionId) {
        Question left = getQuestionOrThrow(leftQuestionId);
        Question right = getQuestionOrThrow(rightQuestionId);
        Long leftGroupId = left.getGroupId();
        Long rightGroupId = right.getGroupId();
        if (leftGroupId != null && leftGroupId.equals(rightGroupId)) {
            return;
        }
        if (leftGroupId != null && rightGroupId != null) {
            mergeQuestionGroups(leftGroupId, rightGroupId);
            return;
        }
        if (leftGroupId != null) {
            moveQuestionToGroup(right, leftGroupId);
            return;
        }
        if (rightGroupId != null) {
            moveQuestionToGroup(left, rightGroupId);
            return;
        }
        Question canonical = left.getId().compareTo(right.getId()) <= 0 ? left : right;
        QuestionGroup group = new QuestionGroup();
        group.setGroupName(limitText(firstText(canonical.getTitle(), "Duplicate question group"), 128));
        group.setCanonicalTitle(limitText(canonical.getTitle(), 255));
        group.setCanonicalAnswer(canonical.getReferenceAnswer());
        group.setMainKnowledgePoint(limitText(canonical.getTitle(), 255));
        group.setDifficulty(canonical.getDifficulty());
        group.setDescription("Auto grouped after SAME_INTENT duplicate review confirmation");
        group.setCategoryId(canonical.getCategoryId());
        group.setStatus(CommonConstants.YES);
        groupMapper.insert(group);
        moveQuestionToGroup(left, group.getId());
        moveQuestionToGroup(right, group.getId());
    }

    private void mergeQuestionGroups(Long leftGroupId, Long rightGroupId) {
        if (leftGroupId == null || rightGroupId == null || leftGroupId.equals(rightGroupId)) {
            return;
        }
        Long targetGroupId = leftGroupId <= rightGroupId ? leftGroupId : rightGroupId;
        Long sourceGroupId = leftGroupId <= rightGroupId ? rightGroupId : leftGroupId;
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .eq(Question::getGroupId, sourceGroupId)
                .set(Question::getGroupId, targetGroupId));
        QuestionGroup targetGroup = groupMapper.selectById(targetGroupId);
        QuestionGroup sourceGroup = groupMapper.selectById(sourceGroupId);
        if (targetGroup != null && sourceGroup != null) {
            fillMissingGroupMetadata(targetGroup, sourceGroup);
            groupMapper.updateById(targetGroup);
        }
        if (sourceGroup != null) {
            sourceGroup.setStatus(CommonConstants.NO);
            sourceGroup.setDescription(limitText(firstText(sourceGroup.getDescription(), "")
                    + " Merged into question_group#" + targetGroupId + " after SAME_INTENT confirmation.", 255));
            groupMapper.updateById(sourceGroup);
        }
    }

    private void fillMissingGroupMetadata(QuestionGroup target, QuestionGroup source) {
        if (!StringUtils.hasText(target.getCanonicalTitle())) {
            target.setCanonicalTitle(source.getCanonicalTitle());
        }
        if (!StringUtils.hasText(target.getCanonicalAnswer())) {
            target.setCanonicalAnswer(source.getCanonicalAnswer());
        }
        if (!StringUtils.hasText(target.getMainKnowledgePoint())) {
            target.setMainKnowledgePoint(source.getMainKnowledgePoint());
        }
        if (!StringUtils.hasText(target.getDifficulty())) {
            target.setDifficulty(source.getDifficulty());
        }
        if (target.getCategoryId() == null) {
            target.setCategoryId(source.getCategoryId());
        }
    }

    private void moveQuestionToGroup(Question question, Long groupId) {
        if (question == null || groupId == null || groupId.equals(question.getGroupId())) {
            return;
        }
        question.setGroupId(groupId);
        questionMapper.updateById(question);
    }

    private boolean relationExists(Long leftQuestionId, Long rightQuestionId) {
        QuestionPairIds pair = sortedPairIds(leftQuestionId, rightQuestionId);
        Long count = relationMapper.selectCount(new LambdaQueryWrapper<QuestionRelation>()
                .eq(QuestionRelation::getSourceQuestionId, pair.sourceQuestionId())
                .eq(QuestionRelation::getTargetQuestionId, pair.targetQuestionId())
                .eq(QuestionRelation::getRelationStatus, QuestionRelationStatus.ACTIVE.name()));
        return count != null && count > 0;
    }

    private boolean duplicateReviewExists(Long leftQuestionId, Long rightQuestionId) {
        QuestionPairIds pair = sortedPairIds(leftQuestionId, rightQuestionId);
        Long count = duplicateReviewMapper.selectCount(new LambdaQueryWrapper<QuestionDuplicateReview>()
                .eq(QuestionDuplicateReview::getSourceQuestionId, pair.sourceQuestionId())
                .eq(QuestionDuplicateReview::getTargetQuestionId, pair.targetQuestionId())
                .in(QuestionDuplicateReview::getReviewStatus,
                        QuestionDuplicateReviewStatus.PENDING.name(),
                        QuestionDuplicateReviewStatus.CONFIRMED.name(),
                        QuestionDuplicateReviewStatus.IGNORED.name()));
        return count != null && count > 0;
    }

    private Question getQuestionOrThrow(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question not found");
        }
        return question;
    }

    private QuestionDuplicateReview getReviewOrThrow(Long id) {
        QuestionDuplicateReview review = duplicateReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question duplicate review not found");
        }
        return review;
    }

    private Map<Long, Question> loadQuestionMap(List<QuestionDuplicateReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (QuestionDuplicateReview review : reviews) {
            if (review.getSourceQuestionId() != null) {
                ids.add(review.getSourceQuestionId());
            }
            if (review.getTargetQuestionId() != null) {
                ids.add(review.getTargetQuestionId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Question> questionMap = new LinkedHashMap<>();
        for (Question question : questionMapper.selectBatchIds(ids)) {
            questionMap.put(question.getId(), question);
        }
        return questionMap;
    }

    private QuestionDuplicateReviewListVO toListVO(QuestionDuplicateReview review) {
        return toListVO(review, null, null);
    }

    private QuestionDuplicateReviewListVO toListVO(QuestionDuplicateReview review, Question source, Question target) {
        QuestionDuplicateReviewListVO vo = new QuestionDuplicateReviewListVO();
        vo.setId(review.getId());
        vo.setSourceQuestionId(review.getSourceQuestionId());
        vo.setSourceTitle(review.getSourceTitleSnapshot());
        vo.setTargetQuestionId(review.getTargetQuestionId());
        vo.setTargetTitle(review.getTargetTitleSnapshot());
        vo.setReviewStatus(review.getReviewStatus());
        vo.setMatchType(review.getMatchType());
        vo.setSimilarityScore(review.getSimilarityScore());
        vo.setMatchReason(review.getMatchReason());
        vo.setScoreBand(firstText(review.getScoreBand(), parseReasonToken(review.getMatchReason(), "scoreBand")));
        vo.setScoreParts(scoreParts(review));
        applyTopLevelScores(vo);
        vo.setSourceGroupId(review.getSourceGroupId());
        vo.setTargetGroupId(review.getTargetGroupId());
        applyDuplicateQuestionContext(vo, source, target);
        vo.setRelationId(review.getRelationId());
        vo.setReviewedBy(review.getReviewedBy());
        vo.setReviewedAt(review.getReviewedAt());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }

    private QuestionDuplicateReviewDetailVO toDetailVO(QuestionDuplicateReview review) {
        QuestionDuplicateReviewDetailVO vo = new QuestionDuplicateReviewDetailVO();
        vo.setId(review.getId());
        vo.setSourceQuestionId(review.getSourceQuestionId());
        vo.setTargetQuestionId(review.getTargetQuestionId());
        vo.setReviewStatus(review.getReviewStatus());
        vo.setMatchType(review.getMatchType());
        vo.setSimilarityScore(review.getSimilarityScore());
        vo.setMatchReason(review.getMatchReason());
        vo.setScoreBand(firstText(review.getScoreBand(), parseReasonToken(review.getMatchReason(), "scoreBand")));
        vo.setScoreParts(scoreParts(review));
        applyTopLevelScores(vo);
        vo.setScoreDetailJson(review.getScoreDetailJson());
        vo.setSourceTitleSnapshot(review.getSourceTitleSnapshot());
        vo.setTargetTitleSnapshot(review.getTargetTitleSnapshot());
        vo.setSourceContentSnapshot(review.getSourceContentSnapshot());
        vo.setTargetContentSnapshot(review.getTargetContentSnapshot());
        vo.setSourceGroupId(review.getSourceGroupId());
        vo.setTargetGroupId(review.getTargetGroupId());
        vo.setCreatedBy(review.getCreatedBy());
        vo.setReviewedBy(review.getReviewedBy());
        vo.setReviewedAt(review.getReviewedAt());
        vo.setIgnoredReason(review.getIgnoredReason());
        vo.setRelationId(review.getRelationId());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setUpdatedAt(review.getUpdatedAt());
        Question source = questionMapper.selectById(review.getSourceQuestionId());
        Question target = questionMapper.selectById(review.getTargetQuestionId());
        applyDuplicateQuestionContext(vo, source, target);
        vo.setSourceQuestion(toSummary(source));
        vo.setTargetQuestion(toSummary(target));
        return vo;
    }

    private void applyDuplicateQuestionContext(QuestionDuplicateReviewListVO vo, Question source, Question target) {
        if (vo == null) {
            return;
        }
        if (source != null) {
            vo.setSourceCategoryId(source.getCategoryId());
            vo.setSourceQuestionType(source.getQuestionType());
            vo.setSourceDifficulty(source.getDifficulty());
        }
        if (target != null) {
            vo.setTargetCategoryId(target.getCategoryId());
            vo.setTargetQuestionType(target.getQuestionType());
            vo.setTargetDifficulty(target.getDifficulty());
        }
        vo.setSameCategory(sameNullableValue(vo.getSourceCategoryId(), vo.getTargetCategoryId()));
        vo.setSameQuestionType(sameText(vo.getSourceQuestionType(), vo.getTargetQuestionType()));
        vo.setSameDifficulty(sameText(vo.getSourceDifficulty(), vo.getTargetDifficulty()));
    }

    private void applyDuplicateQuestionContext(QuestionDuplicateReviewDetailVO vo, Question source, Question target) {
        if (vo == null) {
            return;
        }
        if (source != null) {
            vo.setSourceCategoryId(source.getCategoryId());
            vo.setSourceQuestionType(source.getQuestionType());
            vo.setSourceDifficulty(source.getDifficulty());
        }
        if (target != null) {
            vo.setTargetCategoryId(target.getCategoryId());
            vo.setTargetQuestionType(target.getQuestionType());
            vo.setTargetDifficulty(target.getDifficulty());
        }
        vo.setSameCategory(sameNullableValue(vo.getSourceCategoryId(), vo.getTargetCategoryId()));
        vo.setSameQuestionType(sameText(vo.getSourceQuestionType(), vo.getTargetQuestionType()));
        vo.setSameDifficulty(sameText(vo.getSourceDifficulty(), vo.getTargetDifficulty()));
    }

    private List<QuestionDuplicateReviewListVO.ScorePart> scoreParts(QuestionDuplicateReview review) {
        if (review == null) {
            return List.of();
        }
        List<QuestionDuplicateReviewListVO.ScorePart> persistedParts = parseScoreDetailJson(review.getScoreDetailJson());
        if (!persistedParts.isEmpty()) {
            return persistedParts;
        }
        List<QuestionDuplicateReviewListVO.ScorePart> parts = new ArrayList<>();
        String reason = firstText(review.getMatchReason(), "");
        if (QuestionDuplicateMatchType.SEMANTIC_SIMILAR.name().equals(review.getMatchType())) {
            addScorePart(parts, "vectorScore", "向量", parseReasonScore(reason, "vectorScore"));
            addScorePart(parts, "textScore", "文本", parseReasonScore(reason, "textScore"));
            addScorePart(parts, "finalScore", "综合", parseReasonScore(reason, "finalScore"));
        } else if (review.getSimilarityScore() != null) {
            addScorePart(parts, firstText(review.getMatchType(), "similarity"), "匹配", review.getSimilarityScore());
        }
        return parts;
    }

    private void applyTopLevelScores(QuestionDuplicateReviewListVO vo) {
        if (vo == null || vo.getScoreParts() == null) {
            return;
        }
        for (QuestionDuplicateReviewListVO.ScorePart part : vo.getScoreParts()) {
            if (part == null || part.getScore() == null) {
                continue;
            }
            if ("vectorScore".equals(part.getCode())) {
                vo.setVectorScore(part.getScore());
            } else if ("textScore".equals(part.getCode())) {
                vo.setTextScore(part.getScore());
            } else if ("finalScore".equals(part.getCode())) {
                vo.setFinalScore(part.getScore());
            }
        }
    }

    private void applyTopLevelScores(QuestionDuplicateReviewDetailVO vo) {
        if (vo == null || vo.getScoreParts() == null) {
            return;
        }
        for (QuestionDuplicateReviewListVO.ScorePart part : vo.getScoreParts()) {
            if (part == null || part.getScore() == null) {
                continue;
            }
            if ("vectorScore".equals(part.getCode())) {
                vo.setVectorScore(part.getScore());
            } else if ("textScore".equals(part.getCode())) {
                vo.setTextScore(part.getScore());
            } else if ("finalScore".equals(part.getCode())) {
                vo.setFinalScore(part.getScore());
            }
        }
    }

    private double metadataSimilarityScore(Question source, Question target) {
        double matched = 0D;
        double total = 0D;
        if (source.getCategoryId() != null && target.getCategoryId() != null) {
            total += 1D;
            if (Objects.equals(source.getCategoryId(), target.getCategoryId())) {
                matched += 1D;
            }
        }
        if (StringUtils.hasText(source.getQuestionType()) && StringUtils.hasText(target.getQuestionType())) {
            total += 1D;
            if (source.getQuestionType().equalsIgnoreCase(target.getQuestionType())) {
                matched += 1D;
            }
        }
        if (StringUtils.hasText(source.getDifficulty()) && StringUtils.hasText(target.getDifficulty())) {
            total += 1D;
            if (source.getDifficulty().equalsIgnoreCase(target.getDifficulty())) {
                matched += 1D;
            }
        }
        if (total <= 0D) {
            return 0D;
        }
        return Math.min(1D, matched / total);
    }

    private double tagSimilarityScore(Long sourceQuestionId, Long targetQuestionId) {
        Set<Long> sourceTags = questionTagIds(sourceQuestionId);
        Set<Long> targetTags = questionTagIds(targetQuestionId);
        if (sourceTags.isEmpty() || targetTags.isEmpty()) {
            return 0D;
        }
        Set<Long> union = new LinkedHashSet<>(sourceTags);
        union.addAll(targetTags);
        if (union.isEmpty()) {
            return 0D;
        }
        Set<Long> intersection = new LinkedHashSet<>(sourceTags);
        intersection.retainAll(targetTags);
        return Math.min(1D, (double) intersection.size() / union.size());
    }

    private Set<Long> questionTagIds(Long questionId) {
        if (questionId == null) {
            return Set.of();
        }
        return tagRelationMapper.selectList(new LambdaQueryWrapper<QuestionTagRelation>()
                        .eq(QuestionTagRelation::getQuestionId, questionId)
                        .select(QuestionTagRelation::getTagId))
                .stream()
                .map(QuestionTagRelation::getTagId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void addScorePart(List<QuestionDuplicateReviewListVO.ScorePart> parts, String code, String label,
                              BigDecimal score) {
        if (score == null) {
            return;
        }
        QuestionDuplicateReviewListVO.ScorePart part = new QuestionDuplicateReviewListVO.ScorePart();
        part.setCode(code);
        part.setLabel(scorePartLabel(code, label));
        part.setScore(score);
        parts.add(part);
    }

    private String scorePartLabel(String code, String fallback) {
        if ("vectorScore".equals(code)) {
            return "向量";
        }
        if ("textScore".equals(code)) {
            return "文本";
        }
        if ("metadataScore".equals(code)) {
            return "元数据";
        }
        if ("tagScore".equals(code)) {
            return "标签";
        }
        if ("finalScore".equals(code)) {
            return "综合";
        }
        if (code != null && code.startsWith("HARD_")) {
            return "精确";
        }
        if (code != null && (code.startsWith("TITLE_") || code.startsWith("CONTENT_"))) {
            return "规则";
        }
        return firstText(fallback, code);
    }

    private BigDecimal parseReasonScore(String reason, String key) {
        if (!StringUtils.hasText(reason) || !StringUtils.hasText(key)) {
            return null;
        }
        String marker = key + "=";
        int index = reason.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int start = index + marker.length();
        int end = start;
        while (end < reason.length()) {
            char ch = reason.charAt(end);
            if (!Character.isDigit(ch) && ch != '.') {
                break;
            }
            end++;
        }
        if (end <= start) {
            return null;
        }
        try {
            return score(Double.parseDouble(reason.substring(start, end)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<QuestionDuplicateReviewListVO.ScorePart> parseScoreDetailJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SCORE_DETAIL_TYPE).stream()
                    .map(this::toScorePart)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            log.warn("Question duplicate score detail parse failed", ex);
            return List.of();
        }
    }

    private QuestionDuplicateReviewListVO.ScorePart toScorePart(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object scoreValue = payload.get("score");
        if (scoreValue == null) {
            return null;
        }
        BigDecimal score;
        try {
            score = score(Double.parseDouble(String.valueOf(scoreValue)));
        } catch (NumberFormatException ex) {
            return null;
        }
        QuestionDuplicateReviewListVO.ScorePart part = new QuestionDuplicateReviewListVO.ScorePart();
        String code = stringValue(payload.get("code"));
        part.setCode(code);
        part.setLabel(scorePartLabel(code, stringValue(payload.get("label"))));
        part.setScore(score);
        return part;
    }

    private String parseReasonToken(String reason, String key) {
        if (!StringUtils.hasText(reason) || !StringUtils.hasText(key)) {
            return null;
        }
        String marker = key + "=";
        int index = reason.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int start = index + marker.length();
        int end = reason.indexOf(';', start);
        String value = (end < 0 ? reason.substring(start) : reason.substring(start, end)).trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private double weightedSemanticScore(double vectorScore, double textScore, double metadataScore, double tagScore) {
        double vectorWeight = Math.max(0D, duplicateProperties.getSemanticVectorWeight());
        double textWeight = Math.max(0D, duplicateProperties.getSemanticTextWeight());
        double metadataWeight = Math.max(0D, duplicateProperties.getSemanticMetadataWeight());
        double tagWeight = tagScore > 0D ? Math.max(0D, duplicateProperties.getSemanticTagWeight()) : 0D;
        double weightSum = vectorWeight + textWeight + metadataWeight + tagWeight;
        if (weightSum <= 0D) {
            vectorWeight = 1D;
            textWeight = 0D;
            metadataWeight = 0D;
            tagWeight = 0D;
            weightSum = 1D;
        }
        double weightedScore = (vectorScore * vectorWeight
                + textScore * textWeight
                + metadataScore * metadataWeight
                + tagScore * tagWeight) / weightSum;
        return Math.min(1D, Math.max(0D, weightedScore));
    }

    private Map<String, Object> scorePartPayload(String code, String label, BigDecimal score) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("label", label);
        payload.put("score", score);
        return payload;
    }

    private String writeScoreDetailJson(List<Map<String, Object>> parts) {
        try {
            return objectMapper.writeValueAsString(parts);
        } catch (Exception ex) {
            log.warn("Question duplicate score detail serialize failed", ex);
            return null;
        }
    }

    private QuestionRelationVO toRelationVO(QuestionRelation relation) {
        QuestionRelationVO vo = new QuestionRelationVO();
        vo.setId(relation.getId());
        vo.setSourceQuestionId(relation.getSourceQuestionId());
        vo.setTargetQuestionId(relation.getTargetQuestionId());
        vo.setRelationType(relation.getRelationType());
        vo.setRelationStatus(relation.getRelationStatus());
        vo.setReason(relation.getReason());
        vo.setSimilarityScore(relation.getSimilarityScore());
        vo.setCreatedBy(relation.getCreatedBy());
        vo.setCreatedAt(relation.getCreatedAt());
        vo.setSourceQuestion(toSummary(questionMapper.selectById(relation.getSourceQuestionId())));
        vo.setTargetQuestion(toSummary(questionMapper.selectById(relation.getTargetQuestionId())));
        return vo;
    }

    private QuestionSummaryVO toSummary(Question question) {
        if (question == null) {
            return null;
        }
        QuestionSummaryVO vo = new QuestionSummaryVO();
        vo.setId(question.getId());
        vo.setTitle(question.getTitle());
        vo.setContent(question.getContent());
        vo.setCategoryId(question.getCategoryId());
        vo.setGroupId(question.getGroupId());
        vo.setGroupName(groupName(question.getGroupId()));
        vo.setDifficulty(question.getDifficulty());
        vo.setQuestionType(question.getQuestionType());
        vo.setStatus(question.getStatus());
        return vo;
    }

    private String groupName(Long groupId) {
        if (groupId == null) {
            return null;
        }
        QuestionGroup group = groupMapper.selectById(groupId);
        return group == null ? null : group.getGroupName();
    }

    private QuestionPair sortedPair(Question left, Question right) {
        List<Question> questions = List.of(left, right).stream()
                .sorted(Comparator.comparing(Question::getId))
                .toList();
        return new QuestionPair(questions.get(0), questions.get(1));
    }

    private QuestionPairIds sortedPairIds(Long leftQuestionId, Long rightQuestionId) {
        if (leftQuestionId == null || rightQuestionId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question id is required");
        }
        return leftQuestionId <= rightQuestionId
                ? new QuestionPairIds(leftQuestionId, rightQuestionId)
                : new QuestionPairIds(rightQuestionId, leftQuestionId);
    }

    private QuestionRelationType parseRelationType(String relationType, QuestionRelationType defaultType) {
        if (!StringUtils.hasText(relationType)) {
            return defaultType;
        }
        try {
            return QuestionRelationType.valueOf(relationType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported relationType");
        }
    }

    private BigDecimal score(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limitText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private Boolean sameNullableValue(Object first, Object second) {
        if (first == null || second == null) {
            return null;
        }
        return Objects.equals(first, second);
    }

    private Boolean sameText(String first, String second) {
        if (!StringUtils.hasText(first) || !StringUtils.hasText(second)) {
            return null;
        }
        return first.trim().equalsIgnoreCase(second.trim());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Long> countBy(List<QuestionDuplicateReview> reviews,
                                      java.util.function.Function<QuestionDuplicateReview, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (QuestionDuplicateReview review : reviews) {
            String key = classifier.apply(review);
            if (!StringUtils.hasText(key)) {
                key = "UNKNOWN";
            }
            counts.merge(key, 1L, Long::sum);
        }
        return counts;
    }

    private BigDecimal averageScore(List<QuestionDuplicateReview> reviews) {
        List<BigDecimal> scores = reviews.stream()
                .map(QuestionDuplicateReview::getSimilarityScore)
                .filter(Objects::nonNull)
                .toList();
        if (scores.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private QuestionDuplicateFeedbackStatsVO.Bucket toFeedbackBucket(ScoreBucketRange range,
                                                                     List<QuestionDuplicateReview> reviews) {
        List<QuestionDuplicateReview> bucketReviews = reviews.stream()
                .filter(review -> scoreInRange(review.getSimilarityScore(), range))
                .toList();
        Map<String, Long> statusCounts = countBy(bucketReviews, QuestionDuplicateReview::getReviewStatus);
        long pending = statusCounts.getOrDefault(QuestionDuplicateReviewStatus.PENDING.name(), 0L);
        long confirmed = statusCounts.getOrDefault(QuestionDuplicateReviewStatus.CONFIRMED.name(), 0L);
        long ignored = statusCounts.getOrDefault(QuestionDuplicateReviewStatus.IGNORED.name(), 0L);
        QuestionDuplicateFeedbackStatsVO.Bucket bucket = new QuestionDuplicateFeedbackStatsVO.Bucket();
        bucket.setLabel(range.label());
        bucket.setMinScore(range.minScore());
        bucket.setMaxScore(range.maxScore());
        bucket.setTotalCount((long) bucketReviews.size());
        bucket.setPendingCount(pending);
        bucket.setConfirmedCount(confirmed);
        bucket.setIgnoredCount(ignored);
        bucket.setConfirmationRate(rate(confirmed, confirmed + ignored));
        bucket.setIgnoreRate(rate(ignored, confirmed + ignored));
        return bucket;
    }

    private String buildFeedbackRecommendation(QuestionDuplicateFeedbackStatsVO stats,
                                               List<QuestionDuplicateFeedbackStatsVO.Bucket> buckets) {
        long resolved = stats.getResolvedCount() == null ? 0L : stats.getResolvedCount();
        if (resolved < 10) {
            return "已审核样本偏少，先积累至少 10 条确认/忽略反馈后再调整阈值。";
        }
        QuestionDuplicateFeedbackStatsVO.Bucket strongBucket = findFeedbackBucket(buckets, "90-100");
        QuestionDuplicateFeedbackStatsVO.Bucket reviewBucket = findFeedbackBucket(buckets, "70-79");
        BigDecimal strongIgnoreRate = strongBucket == null ? BigDecimal.ZERO : strongBucket.getIgnoreRate();
        BigDecimal reviewConfirmRate = reviewBucket == null ? BigDecimal.ZERO : reviewBucket.getConfirmationRate();
        if (strongIgnoreRate.compareTo(BigDecimal.valueOf(30)) >= 0) {
            return "高分段忽略率偏高，建议上调 strong 阈值或检查语义权重是否过松。";
        }
        if (reviewConfirmRate.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "70-79 分段确认率较高，建议适度下调 review 阈值以减少漏报。";
        }
        if (stats.getIgnoreRate() != null && stats.getIgnoreRate().compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "整体忽略率偏高，建议先收紧语义审核阈值或增加标题/内容分权重。";
        }
        return "当前反馈分布较平稳，可继续按现有阈值积累样本。";
    }

    private List<String> buildFeedbackWarnings(QuestionDuplicateFeedbackStatsVO stats,
                                               List<QuestionDuplicateFeedbackStatsVO.Bucket> buckets) {
        List<String> warnings = new ArrayList<>();
        long pending = stats.getPendingCount() == null ? 0L : stats.getPendingCount();
        long total = stats.getTotalCount() == null ? 0L : stats.getTotalCount();
        long resolved = stats.getResolvedCount() == null ? 0L : stats.getResolvedCount();
        if (total > 0 && pending * 100 >= total * 60) {
            warnings.add("待审核样本占比超过 60%，反馈闭环覆盖不足。");
        }
        if (resolved < 10) {
            warnings.add("已处理样本少于 10 条，当前确认率只能作为方向参考，建议继续积累样本。");
        }
        QuestionDuplicateFeedbackStatsVO.Bucket strongBucket = findFeedbackBucket(buckets, "90-100");
        if (strongBucket != null && strongBucket.getIgnoredCount() != null && strongBucket.getIgnoredCount() > 0
                && strongBucket.getIgnoreRate() != null
                && strongBucket.getIgnoreRate().compareTo(BigDecimal.valueOf(20)) >= 0) {
            warnings.add("90-100 分段仍有较多忽略记录，建议抽样复核误报来源。");
        }
        QuestionDuplicateFeedbackStatsVO.Bucket lowBucket = findFeedbackBucket(buckets, "70-79");
        if (lowBucket != null && lowBucket.getConfirmedCount() != null && lowBucket.getConfirmedCount() > 0
                && lowBucket.getConfirmationRate() != null
                && lowBucket.getConfirmationRate().compareTo(BigDecimal.valueOf(50)) >= 0) {
            warnings.add("70-79 分段确认率超过 50%，可能存在低分漏报风险。");
        }
        return warnings;
    }

    private QuestionDuplicateFeedbackStatsVO.Bucket findFeedbackBucket(
            List<QuestionDuplicateFeedbackStatsVO.Bucket> buckets, String label) {
        if (buckets == null) {
            return null;
        }
        return buckets.stream()
                .filter(bucket -> label.equals(bucket.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private boolean scoreInRange(BigDecimal score, ScoreBucketRange range) {
        if (score == null) {
            return false;
        }
        boolean aboveMin = score.compareTo(range.minScore()) >= 0;
        boolean belowMax = score.compareTo(range.maxScore()) < 0
                || (range.maxScore().compareTo(BigDecimal.valueOf(100)) >= 0 && score.compareTo(range.maxScore()) <= 0);
        return aboveMin && belowMax;
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String pairKey(Long leftQuestionId, Long rightQuestionId) {
        QuestionPairIds pair = sortedPairIds(leftQuestionId, rightQuestionId);
        return pair.sourceQuestionId() + ":" + pair.targetQuestionId();
    }

    private QuestionDuplicateCheckResultVO emptyResult() {
        QuestionDuplicateCheckResultVO vo = new QuestionDuplicateCheckResultVO();
        vo.setCheckedCount(0);
        vo.setCreatedCount(0);
        vo.setReviewIds(List.of());
        return vo;
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo <= 0 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize <= 0 ? 10L : pageSize;
    }

    private record MatchResult(QuestionDuplicateMatchType matchType, BigDecimal score, String reason,
                               String scoreBand, String scoreDetailJson) {
        private MatchResult(QuestionDuplicateMatchType matchType, BigDecimal score, String reason) {
            this(matchType, score, reason, null, null);
        }
    }

    private record SemanticCandidate(Question question, double score) {
    }

    private record ScoreBucketRange(String label, BigDecimal minScore, BigDecimal maxScore) {
    }

    private record QuestionPair(Question source, Question target) {
    }

    private record QuestionPairIds(Long sourceQuestionId, Long targetQuestionId) {
    }
}
