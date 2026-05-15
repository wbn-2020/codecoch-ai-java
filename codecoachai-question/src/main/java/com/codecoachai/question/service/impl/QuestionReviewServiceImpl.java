package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
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
import com.codecoachai.question.service.QuestionReviewService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionReviewServiceImpl implements QuestionReviewService {

    private static final int MAX_GENERATE_COUNT = 20;

    private final AiQuestionFeignClient aiQuestionFeignClient;
    private final QuestionReviewMapper questionReviewMapper;
    private final QuestionMapper questionMapper;
    private final QuestionCategoryMapper categoryMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionTagMapper tagMapper;
    private final QuestionTagRelationMapper tagRelationMapper;
    private final ObjectMapper objectMapper;

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
        String rawAiResultJson = StringUtils.hasText(aiResponse.getRawResponse())
                ? aiResponse.getRawResponse()
                : toJson(aiResponse);
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

    private void validateGenerateRequest(AiQuestionGenerateRequestDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "request body is required");
        }
        int count = dto.getCount() == null ? 5 : dto.getCount();
        if (count < 1 || count > MAX_GENERATE_COUNT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "count must be between 1 and 20");
        }
    }

    private GenerateQuestionDraftDTO toAiRequest(String batchId, Long adminUserId, AiQuestionGenerateRequestDTO dto) {
        GenerateQuestionDraftDTO request = new GenerateQuestionDraftDTO();
        request.setBatchId(batchId);
        request.setAdminUserId(adminUserId);
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
        request.setExtraRequirements(dto.getExtraRequirements());
        return request;
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question title is required");
        }
        return new ApprovedQuestionPayload(
                title,
                defaultText(dto == null ? null : dto.getContent(), review.getQuestionContent()),
                defaultText(dto == null ? null : dto.getReferenceAnswer(), review.getReferenceAnswer()),
                defaultText(dto == null ? null : dto.getAnalysis(), review.getAnalysis()),
                defaultText(dto == null ? null : dto.getDifficulty(), review.getDifficulty(), "MEDIUM"),
                defaultText(dto == null ? null : dto.getQuestionType(), review.getQuestionType(), "SHORT_ANSWER"),
                dto != null && dto.getCategoryId() != null ? dto.getCategoryId() : review.getCategoryId(),
                dto != null && dto.getGroupId() != null ? dto.getGroupId() : review.getGroupId(),
                tagIds,
                dto != null && dto.getStatus() != null ? dto.getStatus() : CommonConstants.YES,
                dto != null && dto.getIsHighFrequency() != null ? dto.getIsHighFrequency() : CommonConstants.NO,
                defaultText(dto == null ? null : dto.getExperienceLevel(),
                        review.getExperienceYears() == null ? null : String.valueOf(review.getExperienceYears()))
        );
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
        return pageSize == null || pageSize <= 0 ? 10L : pageSize;
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
