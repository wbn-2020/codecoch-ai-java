package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.PracticeRecordQueryDTO;
import com.codecoachai.question.domain.dto.PracticeSubmitDTO;
import com.codecoachai.question.domain.entity.PracticeRecord;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionRecommendationBatch;
import com.codecoachai.question.domain.entity.QuestionRecommendationItem;
import com.codecoachai.question.domain.enums.PracticeReviewStatus;
import com.codecoachai.question.domain.enums.QuestionRecommendationMatchStatus;
import com.codecoachai.question.domain.enums.QuestionRecommendationPracticeStatus;
import com.codecoachai.question.domain.vo.PracticeRecordVO;
import com.codecoachai.question.feign.AiPracticeFeignClient;
import com.codecoachai.question.feign.dto.PracticeReviewDTO;
import com.codecoachai.question.feign.vo.AgentTaskVO;
import com.codecoachai.question.feign.vo.PracticeReviewVO;
import com.codecoachai.question.mapper.PracticeRecordMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRecommendationBatchMapper;
import com.codecoachai.question.mapper.QuestionRecommendationItemMapper;
import com.codecoachai.question.service.PracticeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private static final String DEFAULT_SOURCE = "QUESTION_BANK";

    private final PracticeRecordMapper practiceRecordMapper;
    private final QuestionMapper questionMapper;
    private final QuestionRecommendationItemMapper recommendationItemMapper;
    private final QuestionRecommendationBatchMapper recommendationBatchMapper;
    private final AiPracticeFeignClient aiPracticeFeignClient;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PracticeRecordVO submit(Long questionId, PracticeSubmitDTO dto) {
        Long userId = requireCurrentUserId();
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写练习提交内容");
        }
        if (dto != null && dto.getQuestionId() != null && !dto.getQuestionId().equals(questionId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "questionId is inconsistent");
        }
        Question question = getQuestionOrThrow(questionId);
        RecommendationContext recommendationContext = resolveRecommendationContext(userId, question.getId(), dto);
        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(question.getId());
        record.setAnswerContent(dto.getAnswerContent().trim());
        record.setAnswerDurationSeconds(dto.getAnswerDurationSeconds());
        record.setSource(defaultSource(dto.getSource()));
        applyRecommendationContext(record, recommendationContext, dto);
        record.setReferenceAnswerSnapshot(question.getReferenceAnswer());
        record.setQuestionSnapshotJson(questionSnapshot(question));
        record.setReviewStatus(PracticeReviewStatus.PENDING.name());
        practiceRecordMapper.insert(record);

        try {
            Result<PracticeReviewVO> result = aiPracticeFeignClient.review(toReviewDTO(userId, record, question));
            if (result == null || !result.isSuccess() || result.getData() == null) {
                markFailed(record, result == null ? "AI practice review failed" : result.getMessage());
            } else {
                applyReview(record, result.getData());
            }
        } catch (RuntimeException ex) {
            markFailed(record, ex.getMessage());
        }
        practiceRecordMapper.updateById(record);
        AgentTaskVO completedAgentTask = null;
        if (PracticeReviewStatus.SUCCESS.name().equals(record.getReviewStatus())) {
            markRecommendationCompleted(recommendationContext);
            completedAgentTask = agentBusinessActionNotifier.completeQuestionPractice(
                    userId, resolveTargetJobId(record, dto), record.getId());
        }
        PracticeRecordVO vo = toVO(record, question);
        applyAgentTaskFeedback(vo, completedAgentTask);
        return vo;
    }

    @Override
    public PageResult<PracticeRecordVO> list(PracticeRecordQueryDTO query) {
        Long userId = requireCurrentUserId();
        PracticeRecordQueryDTO actualQuery = query == null ? new PracticeRecordQueryDTO() : query;
        Page<PracticeRecord> page = practiceRecordMapper.selectPage(
                Page.of(defaultPage(actualQuery.getPageNo()), defaultSize(actualQuery.getPageSize())),
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .eq(actualQuery.getQuestionId() != null, PracticeRecord::getQuestionId, actualQuery.getQuestionId())
                        .eq(StringUtils.hasText(actualQuery.getReviewStatus()), PracticeRecord::getReviewStatus,
                                actualQuery.getReviewStatus())
                        .orderByDesc(PracticeRecord::getCreatedAt));
        Map<Long, Question> questionMap = loadQuestionsById(page.getRecords().stream()
                .map(PracticeRecord::getQuestionId)
                .toList());
        return PageResult.of(page.getRecords().stream()
                        .map(record -> toVO(record, questionMap.get(record.getQuestionId())))
                        .toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PracticeRecordVO detail(Long recordId) {
        Long userId = requireCurrentUserId();
        PracticeRecord record = practiceRecordMapper.selectOne(new LambdaQueryWrapper<PracticeRecord>()
                .eq(PracticeRecord::getId, recordId)
                .eq(PracticeRecord::getUserId, userId)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Practice record not found");
        }
        return toVO(record, questionMapper.selectById(record.getQuestionId()));
    }

    private PracticeReviewDTO toReviewDTO(Long userId, PracticeRecord record, Question question) {
        PracticeReviewDTO dto = new PracticeReviewDTO();
        dto.setUserId(userId);
        dto.setRecordId(record.getId());
        dto.setQuestionId(question.getId());
        dto.setQuestionTitle(question.getTitle());
        dto.setQuestionContent(question.getContent());
        dto.setQuestionType(question.getQuestionType());
        dto.setDifficulty(question.getDifficulty());
        dto.setKnowledgePoint(question.getAnalysis());
        dto.setReferenceAnswer(question.getReferenceAnswer());
        dto.setAnalysis(question.getAnalysis());
        dto.setAnswerContent(record.getAnswerContent());
        dto.setAnswerDurationSeconds(record.getAnswerDurationSeconds());
        dto.setExperienceLevel(question.getExperienceLevel());
        return dto;
    }

    private void applyReview(PracticeRecord record, PracticeReviewVO review) {
        record.setReviewStatus(PracticeReviewStatus.SUCCESS.name());
        record.setScore(review.getScore());
        record.setLevel(review.getLevel());
        record.setMasteryStatus(review.getMasteryStatus());
        record.setAiComment(firstText(review.getSummary(), review.getComment()));
        record.setSuggestions(firstText(review.getSuggestions(), join(review.getImprovementSuggestions())));
        record.setKnowledgePoints(review.getKnowledgePoints());
        record.setStrengths(join(review.getStrengths()));
        record.setWeaknesses(join(review.getWeaknesses()));
        record.setImprovementSuggestions(join(review.getImprovementSuggestions()));
        record.setReferenceComparison(review.getReferenceComparison());
        record.setKnowledgeGaps(join(review.getKnowledgeGaps()));
        record.setSuggestedFollowUps(join(review.getSuggestedFollowUps()));
        record.setReviewJson(reviewJson(review));
        record.setAiCallLogId(review.getAiCallLogId());
        record.setErrorMessage(null);
    }

    private void markFailed(PracticeRecord record, String errorMessage) {
        record.setReviewStatus(PracticeReviewStatus.FAILED.name());
        record.setErrorMessage(safeError(errorMessage));
    }

    private PracticeRecordVO toVO(PracticeRecord record, Question question) {
        PracticeRecordVO vo = new PracticeRecordVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setQuestionId(record.getQuestionId());
        vo.setQuestionTitle(question == null ? null : question.getTitle());
        vo.setQuestionType(question == null ? null : question.getQuestionType());
        vo.setDifficulty(question == null ? null : question.getDifficulty());
        vo.setKnowledgePoint(question == null ? null : question.getAnalysis());
        vo.setAnswerContent(record.getAnswerContent());
        vo.setUserAnswer(record.getAnswerContent());
        vo.setAnswerDurationSeconds(record.getAnswerDurationSeconds());
        vo.setSource(record.getSource());
        vo.setRecommendationItemId(record.getRecommendationItemId());
        vo.setBatchId(record.getBatchId());
        vo.setSourceType(record.getSourceType());
        vo.setSourceId(record.getSourceId());
        vo.setSkillProfileId(record.getSkillProfileId());
        vo.setStudyPlanId(record.getStudyPlanId());
        vo.setReviewStatus(record.getReviewStatus());
        vo.setScore(record.getScore());
        vo.setLevel(record.getLevel());
        vo.setMasteryStatus(record.getMasteryStatus());
        vo.setSummary(record.getAiComment());
        vo.setAiComment(record.getAiComment());
        vo.setSuggestions(record.getSuggestions());
        vo.setKnowledgePoints(record.getKnowledgePoints());
        vo.setStrengths(record.getStrengths());
        vo.setWeaknesses(record.getWeaknesses());
        vo.setImprovementSuggestions(record.getImprovementSuggestions());
        vo.setReferenceComparison(record.getReferenceComparison());
        vo.setKnowledgeGaps(record.getKnowledgeGaps());
        vo.setSuggestedFollowUps(record.getSuggestedFollowUps());
        vo.setReferenceAnswer(record.getReferenceAnswerSnapshot());
        vo.setReferenceAnswerSnapshot(record.getReferenceAnswerSnapshot());
        vo.setAiCallLogId(record.getAiCallLogId());
        vo.setErrorMessage(record.getErrorMessage());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private Question getQuestionOrThrow(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || !CommonConstants.YES.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question not found");
        }
        return question;
    }

    private Map<Long, Question> loadQuestionsById(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = questionIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Question> questionMap = new LinkedHashMap<>();
        for (Question question : questionMapper.selectBatchIds(ids)) {
            if (question != null && question.getId() != null) {
                questionMap.put(question.getId(), question);
            }
        }
        return questionMap;
    }

    private RecommendationContext resolveRecommendationContext(Long userId, Long questionId, PracticeSubmitDTO dto) {
        if (dto == null || dto.getRecommendationItemId() == null) {
            return null;
        }
        QuestionRecommendationItem item = recommendationItemMapper.selectOne(
                new LambdaQueryWrapper<QuestionRecommendationItem>()
                        .eq(QuestionRecommendationItem::getId, dto.getRecommendationItemId())
                        .eq(QuestionRecommendationItem::getUserId, userId)
                        .eq(QuestionRecommendationItem::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (item == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Recommendation item not found");
        }
        if (item.getQuestionId() == null
                || QuestionRecommendationPracticeStatus.NOT_PRACTICABLE.getCode().equals(item.getPracticeStatus())
                || QuestionRecommendationMatchStatus.UNMATCHED_DRAFT.getCode().equals(item.getMatchStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Recommendation item has no official question to practice");
        }
        if (!questionId.equals(item.getQuestionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Recommendation item does not match questionId");
        }
        QuestionRecommendationBatch batch = recommendationBatchMapper.selectOne(
                new LambdaQueryWrapper<QuestionRecommendationBatch>()
                        .eq(QuestionRecommendationBatch::getId, item.getBatchId())
                        .eq(QuestionRecommendationBatch::getUserId, userId)
                        .eq(QuestionRecommendationBatch::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Recommendation batch not found");
        }
        if (dto.getBatchId() != null && !dto.getBatchId().equals(batch.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "batchId is inconsistent");
        }
        return new RecommendationContext(item, batch);
    }

    private void applyRecommendationContext(PracticeRecord record, RecommendationContext context,
                                            PracticeSubmitDTO dto) {
        if (context != null) {
            QuestionRecommendationItem item = context.item();
            QuestionRecommendationBatch batch = context.batch();
            record.setRecommendationItemId(item.getId());
            record.setBatchId(batch.getId());
            record.setSourceType(batch.getSourceType());
            record.setSourceId(batch.getSourceId());
            record.setSkillProfileId(batch.getSkillProfileId());
            record.setStudyPlanId(batch.getStudyPlanId());
            return;
        }
        record.setRecommendationItemId(dto.getRecommendationItemId());
        record.setBatchId(dto.getBatchId());
        if (dto.getTargetJobId() != null && (!StringUtils.hasText(dto.getSourceType()) || dto.getSourceId() == null)) {
            record.setSourceType("TARGET_JOB");
            record.setSourceId(dto.getTargetJobId());
        } else {
            record.setSourceType(dto.getSourceType());
            record.setSourceId(dto.getSourceId());
        }
        record.setSkillProfileId(dto.getSkillProfileId());
        record.setStudyPlanId(dto.getStudyPlanId());
    }

    private void markRecommendationCompleted(RecommendationContext context) {
        if (context == null) {
            return;
        }
        QuestionRecommendationItem item = context.item();
        item.setPracticeStatus(QuestionRecommendationPracticeStatus.COMPLETED.getCode());
        recommendationItemMapper.updateById(item);
    }

    private Long resolveTargetJobId(PracticeRecord record, PracticeSubmitDTO dto) {
        if (dto != null && dto.getTargetJobId() != null) {
            return dto.getTargetJobId();
        }
        if (record != null && "TARGET_JOB".equalsIgnoreCase(record.getSourceType())) {
            return record.getSourceId();
        }
        return null;
    }

    private void applyAgentTaskFeedback(PracticeRecordVO vo, AgentTaskVO task) {
        if (vo == null || task == null || task.getId() == null) {
            if (vo != null) {
                vo.setAgentTaskCompleted(false);
            }
            return;
        }
        vo.setAgentTaskCompleted("DONE".equalsIgnoreCase(task.getStatus()));
        vo.setAgentTaskId(task.getId());
        vo.setAgentTaskTitle(task.getTitle());
        vo.setAgentTaskStatus(task.getStatus());
        vo.setAgentReviewSummary(task.getReviewSummary());
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 10L : Math.min(pageSize, 100L);
    }

    private String safeError(String message) {
        return "AI 点评暂时不可用，请稍后重试，或先查看参考解析继续练习。";
    }

    private String defaultSource(String source) {
        return StringUtils.hasText(source) ? source.trim() : DEFAULT_SOURCE;
    }

    private String questionSnapshot(Question question) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", question.getId());
        snapshot.put("title", question.getTitle());
        snapshot.put("content", question.getContent());
        snapshot.put("questionType", question.getQuestionType());
        snapshot.put("difficulty", question.getDifficulty());
        snapshot.put("analysis", question.getAnalysis());
        snapshot.put("experienceLevel", question.getExperienceLevel());
        return toJson(snapshot);
    }

    private String reviewJson(PracticeReviewVO review) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("score", review.getScore());
        json.put("level", review.getLevel());
        json.put("summary", firstText(review.getSummary(), review.getComment()));
        json.put("strengths", review.getStrengths());
        json.put("weaknesses", review.getWeaknesses());
        json.put("improvementSuggestions", review.getImprovementSuggestions());
        json.put("referenceComparison", review.getReferenceComparison());
        json.put("knowledgeGaps", review.getKnowledgeGaps());
        json.put("suggestedFollowUps", review.getSuggestedFollowUps());
        return toJson(json);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join("\n", values);
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

    private record RecommendationContext(QuestionRecommendationItem item, QuestionRecommendationBatch batch) {
    }
}
