package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.domain.dto.InnerSelectQuestionDTO;
import com.codecoachai.question.domain.dto.RecommendQuestionDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionReview;
import com.codecoachai.question.domain.enums.QuestionReviewStatus;
import com.codecoachai.question.domain.vo.InnerQuestionVO;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionReviewMapper;
import com.codecoachai.question.service.QuestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/questions")
public class InnerQuestionController {

    private final QuestionService questionService;
    private final QuestionMapper questionMapper;
    private final QuestionReviewMapper questionReviewMapper;
    private final ObjectMapper objectMapper;

    @PostMapping("/select")
    public Result<InnerQuestionVO> select(@RequestBody InnerSelectQuestionDTO dto) {
        return Result.success(questionService.selectForInterview(dto));
    }

    @PostMapping("/pick-for-interview")
    public Result<InnerQuestionVO> pickForInterview(@RequestBody InnerSelectQuestionDTO dto) {
        return Result.success(questionService.selectForInterview(dto));
    }

    @GetMapping("/{id}")
    public Result<InnerQuestionVO> getQuestion(@PathVariable Long id) {
        return Result.success(questionService.getInnerQuestion(id));
    }

    @GetMapping("/recommend")
    public Result<List<InnerQuestionVO>> recommendByGet() {
        return Result.success(questionService.recommend(new RecommendQuestionDTO()));
    }

    @PostMapping("/recommend-for-report")
    public Result<List<InnerQuestionVO>> recommend(@RequestBody RecommendQuestionDTO dto) {
        return Result.success(questionService.recommend(dto));
    }

    @PostMapping("/reviews/save-drafts")
    public Result<SaveQuestionDraftsVO> saveDrafts(@RequestBody SaveQuestionDraftsDTO dto) {
        validateSaveDrafts(dto);
        List<Long> reviewIds = new ArrayList<>();
        String rawJson = StringUtils.hasText(dto.getRawAiResultJson()) ? dto.getRawAiResultJson() : toJson(dto);
        for (QuestionDraftItem item : dto.getQuestions()) {
            if (!StringUtils.hasText(item.getTitle()) || !StringUtils.hasText(item.getContent())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "题目草稿标题和内容不能为空");
            }
            QuestionReview review = new QuestionReview();
            review.setBatchId(dto.getBatchId());
            review.setCreatedBy(dto.getCreatedBy());
            review.setReviewStatus(QuestionReviewStatus.PENDING.name());
            review.setAiCallLogId(dto.getAiCallLogId());
            review.setTargetPosition(dto.getTargetPosition());
            review.setTechnologyStack(dto.getTechnologyStack());
            review.setKnowledgePoint(dto.getKnowledgePoint());
            review.setQuestionType(defaultText(item.getQuestionType(), defaultText(dto.getQuestionType(), "SHORT_ANSWER")));
            review.setDifficulty(defaultText(item.getDifficulty(), defaultText(dto.getDifficulty(), "MEDIUM")));
            review.setExperienceYears(dto.getExperienceYears());
            review.setRawAiResultJson(rawJson);
            review.setQuestionTitle(item.getTitle());
            review.setQuestionContent(item.getContent());
            review.setReferenceAnswer(item.getReferenceAnswer());
            review.setAnalysis(item.getAnalysis());
            review.setFollowUpQuestionsJson(toJson(item.getFollowUpQuestions()));
            review.setTagSuggestionsJson(toJson(item.getTagSuggestions()));
            review.setCategorySuggestion(item.getCategorySuggestion());
            review.setGroupSuggestion(item.getGroupSuggestion());
            questionReviewMapper.insert(review);
            reviewIds.add(review.getId());
        }
        SaveQuestionDraftsVO vo = new SaveQuestionDraftsVO();
        vo.setBatchId(dto.getBatchId());
        vo.setSavedCount(reviewIds.size());
        vo.setReviewIds(reviewIds);
        return Result.success(vo);
    }

    @GetMapping("/{id}/search-doc")
    public Result<Map<String, Object>> getSearchDoc(@PathVariable Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            return Result.success(null);
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", q.getId());
        doc.put("title", q.getTitle());
        doc.put("content", q.getContent());
        doc.put("referenceAnswer", q.getReferenceAnswer());
        doc.put("difficulty", q.getDifficulty());
        doc.put("questionType", q.getQuestionType());
        doc.put("experienceLevel", q.getExperienceLevel());
        doc.put("categoryId", q.getCategoryId());
        doc.put("groupId", q.getGroupId());
        doc.put("isHighFrequency", q.getIsHighFrequency());
        doc.put("status", q.getStatus());
        return Result.success(doc);
    }

    private void validateSaveDrafts(SaveQuestionDraftsDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getBatchId()) || dto.getCreatedBy() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "batchId and createdBy are required");
        }
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "questions are required");
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "serialize question draft failed");
        }
    }

    @Data
    public static class SaveQuestionDraftsDTO {
        private String batchId;
        private Long createdBy;
        private Long aiCallLogId;
        private String targetPosition;
        private String technologyStack;
        private String knowledgePoint;
        private String questionType;
        private String difficulty;
        private Integer experienceYears;
        private String rawAiResultJson;
        private List<QuestionDraftItem> questions;
    }

    @Data
    public static class QuestionDraftItem {
        private String title;
        private String content;
        private String referenceAnswer;
        private String analysis;
        private List<String> followUpQuestions;
        private List<String> tagSuggestions;
        private String categorySuggestion;
        private String difficulty;
        private String questionType;
        private String groupSuggestion;
    }

    @Data
    public static class SaveQuestionDraftsVO {
        private String batchId;
        private Integer savedCount;
        private List<Long> reviewIds;
    }
}
