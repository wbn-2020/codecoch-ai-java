package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.codecoachai.question.domain.dto.QuestionDuplicateCheckDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionRelationCreateDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionDuplicateReview;
import com.codecoachai.question.domain.entity.QuestionGroup;
import com.codecoachai.question.domain.entity.QuestionRelation;
import com.codecoachai.question.domain.enums.QuestionDuplicateMatchType;
import com.codecoachai.question.domain.enums.QuestionDuplicateReviewStatus;
import com.codecoachai.question.domain.enums.QuestionRelationStatus;
import com.codecoachai.question.domain.enums.QuestionRelationType;
import com.codecoachai.question.domain.vo.QuestionDuplicateCheckResultVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO;
import com.codecoachai.question.domain.vo.QuestionRelationVO;
import com.codecoachai.question.domain.vo.QuestionSummaryVO;
import com.codecoachai.question.feign.AiEmbeddingFeignClient;
import com.codecoachai.question.feign.dto.EmbeddingRequestDTO;
import com.codecoachai.question.feign.vo.EmbeddingResponseVO;
import com.codecoachai.question.mapper.QuestionDuplicateReviewMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRelationMapper;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
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
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionDuplicateServiceImpl implements QuestionDuplicateService {

    private static final int MAX_BATCH_CHECK_COUNT = 100;
    private static final int MAX_TARGET_CANDIDATE_COUNT = 200;
    private static final int MAX_VECTOR_SEED_COUNT = 300;
    private static final int VECTOR_SEARCH_LIMIT = 30;
    private static final int EMBEDDING_BATCH_SIZE = 64;
    private static final double SEMANTIC_SIMILAR_THRESHOLD = 0.82D;
    private static final String QUESTION_COLLECTION = "question_embedding";

    private final QuestionMapper questionMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionDuplicateReviewMapper duplicateReviewMapper;
    private final QuestionRelationMapper relationMapper;
    private final AiEmbeddingFeignClient aiEmbeddingFeignClient;
    private final VectorStoreClient vectorStoreClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDuplicateCheckResultVO checkDuplicate(QuestionDuplicateCheckDTO dto) {
        Long operatorId = SecurityAssert.requireLoginUserId();
        List<Long> questionIds = normalizeQuestionIds(dto);
        if (questionIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "questionId or questionIds is required");
        }
        if (questionIds.size() > MAX_BATCH_CHECK_COUNT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question duplicate check limit is 100");
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
        return PageResult.of(page.getRecords().stream().map(this::toListVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
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

    private List<Question> findTargetCandidates(Question source) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .ne(Question::getId, source.getId())
                .eq(Question::getStatus, CommonConstants.YES)
                .eq(source.getCategoryId() != null, Question::getCategoryId, source.getCategoryId())
                .eq(StringUtils.hasText(source.getQuestionType()), Question::getQuestionType, source.getQuestionType())
                .eq(StringUtils.hasText(source.getDifficulty()), Question::getDifficulty, source.getDifficulty())
                .orderByDesc(Question::getUpdatedAt)
                .last("limit " + MAX_TARGET_CANDIDATE_COUNT);
        return questionMapper.selectList(wrapper);
    }

    private MatchResult match(Question source, Question target) {
        String sourceTitle = source.getTitle();
        String targetTitle = target.getTitle();
        if (StringUtils.hasText(sourceTitle) && sourceTitle.trim().equals(targetTitle == null ? null : targetTitle.trim())) {
            return new MatchResult(QuestionDuplicateMatchType.TITLE_EXACT, score(100D), "title exact match");
        }
        String normalizedSourceTitle = QuestionTextNormalizeUtils.normalizeTitle(sourceTitle);
        String normalizedTargetTitle = QuestionTextNormalizeUtils.normalizeTitle(targetTitle);
        if (StringUtils.hasText(normalizedSourceTitle) && normalizedSourceTitle.equals(normalizedTargetTitle)) {
            return new MatchResult(QuestionDuplicateMatchType.TITLE_NORMALIZED_EQUAL, score(95D),
                    "normalized title match");
        }
        double titleJaccard = QuestionTextNormalizeUtils.jaccard(sourceTitle, targetTitle);
        double titleLevenshtein = QuestionTextNormalizeUtils.levenshteinSimilarity(sourceTitle, targetTitle);
        double titleSimilarity = Math.max(titleJaccard, titleLevenshtein);
        if (titleJaccard >= 0.75D || titleLevenshtein >= 0.82D) {
            return new MatchResult(QuestionDuplicateMatchType.TITLE_SIMILAR, score(titleSimilarity * 100D),
                    "title similarity match");
        }
        double contentSimilarity = QuestionTextNormalizeUtils.jaccard(
                QuestionTextNormalizeUtils.snapshot(source.getContent(), 500),
                QuestionTextNormalizeUtils.snapshot(target.getContent(), 500));
        if (contentSimilarity >= 0.70D) {
            return new MatchResult(QuestionDuplicateMatchType.CONTENT_SIMILAR, score(contentSimilarity * 100D),
                    "content similarity match");
        }
        return null;
    }

    private MatchResult semanticMatch(Question source, Question target, double vectorScore) {
        if (vectorScore < SEMANTIC_SIMILAR_THRESHOLD) {
            return null;
        }
        double titleSimilarity = Math.max(
                QuestionTextNormalizeUtils.jaccard(source.getTitle(), target.getTitle()),
                QuestionTextNormalizeUtils.levenshteinSimilarity(source.getTitle(), target.getTitle()));
        double contentSimilarity = QuestionTextNormalizeUtils.jaccard(
                QuestionTextNormalizeUtils.snapshot(source.getContent(), 500),
                QuestionTextNormalizeUtils.snapshot(target.getContent(), 500));
        double textScore = Math.max(titleSimilarity, contentSimilarity);
        double finalScore = Math.min(1D, vectorScore * 0.78D + textScore * 0.22D);
        String reason = "semantic vector match; vectorScore=" + score(vectorScore * 100D)
                + "; textScore=" + score(textScore * 100D)
                + "; finalScore=" + score(finalScore * 100D);
        return new MatchResult(QuestionDuplicateMatchType.SEMANTIC_SIMILAR, score(finalScore * 100D), reason);
    }

    private List<SemanticCandidate> findSemanticCandidates(Question source, List<Question> ruleCandidates) {
        if (!vectorStoreClient.isEnabled()) {
            return List.of();
        }
        try {
            List<Question> seedQuestions = mergeSeedQuestions(source, ruleCandidates, findVectorSeedCandidates(source));
            Map<Long, List<Float>> vectors = embedQuestionVectors(seedQuestions);
            List<Float> sourceVector = vectors.get(source.getId());
            if (sourceVector == null || sourceVector.isEmpty()) {
                return List.of();
            }
            vectorStoreClient.ensureCollection(QUESTION_COLLECTION, sourceVector.size());
            upsertQuestionVectors(seedQuestions, vectors);

            List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                    .collectionName(QUESTION_COLLECTION)
                    .vector(sourceVector)
                    .mustMatchPayload(questionVectorFilter(source))
                    .limit(VECTOR_SEARCH_LIMIT)
                    .build());
            if (hits.isEmpty()) {
                return List.of();
            }
            Set<Long> hitQuestionIds = new LinkedHashSet<>();
            Map<Long, Double> scoreMap = new LinkedHashMap<>();
            for (VectorSearchResult hit : hits) {
                Long questionId = payloadLong(hit.getPayload(), "questionId");
                if (questionId == null || Objects.equals(questionId, source.getId())) {
                    continue;
                }
                hitQuestionIds.add(questionId);
                scoreMap.putIfAbsent(questionId, hit.getScore());
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

    private List<Question> findVectorSeedCandidates(Question source) {
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .ne(Question::getId, source.getId())
                .eq(Question::getStatus, CommonConstants.YES)
                .eq(source.getCategoryId() != null, Question::getCategoryId, source.getCategoryId())
                .eq(StringUtils.hasText(source.getQuestionType()), Question::getQuestionType, source.getQuestionType())
                .orderByDesc(Question::getUpdatedAt)
                .last("limit " + MAX_VECTOR_SEED_COUNT));
    }

    private List<Question> mergeSeedQuestions(Question source, List<Question> ruleCandidates,
                                              List<Question> vectorSeedCandidates) {
        Map<Long, Question> seedMap = new LinkedHashMap<>();
        seedMap.put(source.getId(), source);
        for (Question question : ruleCandidates) {
            seedMap.put(question.getId(), question);
        }
        for (Question question : vectorSeedCandidates) {
            seedMap.put(question.getId(), question);
        }
        return seedMap.values().stream().toList();
    }

    private Map<Long, List<Float>> embedQuestionVectors(List<Question> questions) {
        Map<Long, List<Float>> vectorMap = new LinkedHashMap<>();
        for (int start = 0; start < questions.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(questions.size(), start + EMBEDDING_BATCH_SIZE);
            List<Question> batch = questions.subList(start, end);
            EmbeddingRequestDTO request = new EmbeddingRequestDTO();
            request.setTexts(batch.stream().map(this::questionVectorText).toList());
            Result<EmbeddingResponseVO> response = aiEmbeddingFeignClient.embeddings(request);
            if (response == null || !response.isSuccess() || response.getData() == null
                    || response.getData().getVectors() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI embedding response is empty");
            }
            List<List<Float>> vectors = response.getData().getVectors();
            if (vectors.size() != batch.size()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI embedding vector count mismatch");
            }
            for (int i = 0; i < batch.size(); i++) {
                vectorMap.put(batch.get(i).getId(), vectors.get(i));
            }
        }
        return vectorMap;
    }

    private void upsertQuestionVectors(List<Question> questions, Map<Long, List<Float>> vectors) {
        List<VectorPoint> points = new ArrayList<>();
        for (Question question : questions) {
            List<Float> vector = vectors.get(question.getId());
            if (vector == null || vector.isEmpty()) {
                continue;
            }
            points.add(VectorPoint.builder()
                    .id(questionPointId(question.getId()))
                    .vector(vector)
                    .payload(questionPayload(question))
                    .build());
        }
        vectorStoreClient.upsert(QUESTION_COLLECTION, points);
    }

    private Map<String, Object> questionPayload(Question question) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", question.getId());
        payload.put("status", CommonConstants.YES);
        if (question.getCategoryId() != null) {
            payload.put("categoryId", question.getCategoryId());
        }
        if (StringUtils.hasText(question.getQuestionType())) {
            payload.put("questionType", question.getQuestionType());
        }
        if (StringUtils.hasText(question.getDifficulty())) {
            payload.put("difficulty", question.getDifficulty());
        }
        return payload;
    }

    private Map<String, Object> questionVectorFilter(Question source) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("status", CommonConstants.YES);
        if (source.getCategoryId() != null) {
            filter.put("categoryId", source.getCategoryId());
        }
        if (StringUtils.hasText(source.getQuestionType())) {
            filter.put("questionType", source.getQuestionType());
        }
        return filter;
    }

    private String questionVectorText(Question question) {
        return firstText(question.getTitle(), "") + "\n"
                + firstText(question.getContent(), "") + "\n"
                + firstText(question.getReferenceAnswer(), "") + "\n"
                + firstText(question.getAnalysis(), "");
    }

    private String questionPointId(Long questionId) {
        return "question-" + questionId;
    }

    private Long payloadLong(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
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
                .eq(QuestionDuplicateReview::getTargetQuestionId, pair.targetQuestionId()));
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

    private QuestionDuplicateReviewListVO toListVO(QuestionDuplicateReview review) {
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
        vo.setSourceGroupId(review.getSourceGroupId());
        vo.setTargetGroupId(review.getTargetGroupId());
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
        vo.setSourceQuestion(toSummary(questionMapper.selectById(review.getSourceQuestionId())));
        vo.setTargetQuestion(toSummary(questionMapper.selectById(review.getTargetQuestionId())));
        return vo;
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

    private record MatchResult(QuestionDuplicateMatchType matchType, BigDecimal score, String reason) {
    }

    private record SemanticCandidate(Question question, double score) {
    }

    private record QuestionPair(Question source, Question target) {
    }

    private record QuestionPairIds(Long sourceQuestionId, Long targetQuestionId) {
    }
}
