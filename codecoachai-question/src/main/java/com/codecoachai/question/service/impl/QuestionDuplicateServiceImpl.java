package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionDuplicateServiceImpl implements QuestionDuplicateService {

    private static final int MAX_BATCH_CHECK_COUNT = 100;
    private static final int MAX_TARGET_CANDIDATE_COUNT = 200;

    private final QuestionMapper questionMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionDuplicateReviewMapper duplicateReviewMapper;
    private final QuestionRelationMapper relationMapper;

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
            for (Question target : findTargetCandidates(source)) {
                MatchResult match = match(source, target);
                if (match == null || relationExists(source.getId(), target.getId())
                        || duplicateReviewExists(source.getId(), target.getId())) {
                    continue;
                }
                QuestionDuplicateReview review = buildReview(source, target, match, operatorId);
                duplicateReviewMapper.insert(review);
                createdIds.add(review.getId());
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

    private record QuestionPair(Question source, Question target) {
    }

    private record QuestionPairIds(Long sourceQuestionId, Long targetQuestionId) {
    }
}
