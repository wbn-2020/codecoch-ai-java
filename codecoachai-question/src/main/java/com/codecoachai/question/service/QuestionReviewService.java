package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.dto.QuestionReviewApproveDTO;
import com.codecoachai.question.domain.dto.QuestionReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionReviewRejectDTO;
import com.codecoachai.question.domain.vo.AiQuestionGenerateResultVO;
import com.codecoachai.question.domain.vo.QuestionReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionReviewListVO;

public interface QuestionReviewService {

    AiQuestionGenerateResultVO generate(AiQuestionGenerateRequestDTO dto);

    PageResult<QuestionReviewListVO> pageReviews(QuestionReviewQueryDTO query);

    QuestionReviewDetailVO getReview(Long id);

    QuestionReviewDetailVO approve(Long id, QuestionReviewApproveDTO dto);

    QuestionReviewDetailVO reject(Long id, QuestionReviewRejectDTO dto);
}
