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
import com.codecoachai.question.domain.enums.PracticeReviewStatus;
import com.codecoachai.question.domain.vo.PracticeRecordVO;
import com.codecoachai.question.feign.AiPracticeFeignClient;
import com.codecoachai.question.feign.dto.PracticeReviewDTO;
import com.codecoachai.question.feign.vo.PracticeReviewVO;
import com.codecoachai.question.mapper.PracticeRecordMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.PracticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private final PracticeRecordMapper practiceRecordMapper;
    private final QuestionMapper questionMapper;
    private final AiPracticeFeignClient aiPracticeFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PracticeRecordVO submit(Long questionId, PracticeSubmitDTO dto) {
        Long userId = requireCurrentUserId();
        Question question = getQuestionOrThrow(questionId);
        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(question.getId());
        record.setAnswerContent(dto.getAnswerContent());
        record.setReferenceAnswer(question.getReferenceAnswer());
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
        return toVO(record, question);
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
        return PageResult.of(page.getRecords().stream()
                        .map(record -> toVO(record, questionMapper.selectById(record.getQuestionId())))
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
        dto.setReferenceAnswer(question.getReferenceAnswer());
        dto.setAnswerContent(record.getAnswerContent());
        return dto;
    }

    private void applyReview(PracticeRecord record, PracticeReviewVO review) {
        record.setReviewStatus(PracticeReviewStatus.SUCCESS.name());
        record.setScore(review.getScore());
        record.setMasteryStatus(review.getMasteryStatus());
        record.setAiComment(review.getComment());
        record.setSuggestions(review.getSuggestions());
        record.setKnowledgePoints(review.getKnowledgePoints());
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
        vo.setAnswerContent(record.getAnswerContent());
        vo.setReviewStatus(record.getReviewStatus());
        vo.setScore(record.getScore());
        vo.setMasteryStatus(record.getMasteryStatus());
        vo.setAiComment(record.getAiComment());
        vo.setSuggestions(record.getSuggestions());
        vo.setKnowledgePoints(record.getKnowledgePoints());
        vo.setReferenceAnswer(record.getReferenceAnswer());
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

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null ? 10L : pageSize;
    }

    private String safeError(String message) {
        if (!StringUtils.hasText(message)) {
            return "AI practice review failed";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
