package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.QuestionDuplicateCheckDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionRelationCreateDTO;
import com.codecoachai.question.domain.vo.QuestionDuplicateCheckResultVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO;
import com.codecoachai.question.domain.vo.QuestionRelationVO;
import java.util.List;

public interface QuestionDuplicateService {

    QuestionDuplicateCheckResultVO checkDuplicate(QuestionDuplicateCheckDTO dto);

    QuestionDuplicateCheckResultVO checkDuplicateForQuestion(Long questionId, Long operatorId);

    PageResult<QuestionDuplicateReviewListVO> pageReviews(QuestionDuplicateReviewQueryDTO query);

    QuestionDuplicateReviewDetailVO getReview(Long id);

    QuestionDuplicateReviewDetailVO merge(Long id, QuestionDuplicateMergeDTO dto);

    QuestionDuplicateReviewDetailVO ignore(Long id, QuestionDuplicateIgnoreDTO dto);

    List<QuestionRelationVO> listRelations(Long questionId);

    QuestionRelationVO createRelation(Long questionId, QuestionRelationCreateDTO dto);

    void deleteRelation(Long questionId, Long relationId);
}
