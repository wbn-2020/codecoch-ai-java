package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.BatchQuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.BatchQuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateCheckDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvaluationDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionRelationCreateDTO;
import com.codecoachai.question.domain.vo.QuestionDuplicateCheckResultVO;
import com.codecoachai.question.domain.vo.BatchQuestionDuplicateResultVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvaluationVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateFeedbackStatsVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO;
import com.codecoachai.question.domain.vo.QuestionRelationVO;
import java.util.List;

public interface QuestionDuplicateService {

    QuestionDuplicateCheckResultVO checkDuplicate(QuestionDuplicateCheckDTO dto);

    QuestionDuplicateCheckResultVO checkDuplicateForQuestion(Long questionId, Long operatorId);

    PageResult<QuestionDuplicateReviewListVO> pageReviews(QuestionDuplicateReviewQueryDTO query);

    QuestionDuplicateFeedbackStatsVO feedbackStats();

    QuestionDuplicateEvaluationVO evaluate(QuestionDuplicateEvaluationDTO dto);

    QuestionDuplicateReviewDetailVO getReview(Long id);

    QuestionDuplicateReviewDetailVO merge(Long id, QuestionDuplicateMergeDTO dto);

    QuestionDuplicateReviewDetailVO ignore(Long id, QuestionDuplicateIgnoreDTO dto);

    /** 批量确认去重审核单。单条失败不影响其他条目，失败明细随结果返回。 */
    BatchQuestionDuplicateResultVO batchMerge(BatchQuestionDuplicateMergeDTO dto);

    /** 批量忽略去重审核单。单条失败不影响其他条目，失败明细随结果返回。 */
    BatchQuestionDuplicateResultVO batchIgnore(BatchQuestionDuplicateIgnoreDTO dto);

    List<QuestionRelationVO> listRelations(Long questionId);

    QuestionRelationVO createRelation(Long questionId, QuestionRelationCreateDTO dto);

    void deleteRelation(Long questionId, Long relationId);

    void invalidatePendingReviewsForQuestion(Long questionId, Long operatorId, String reason);
}
