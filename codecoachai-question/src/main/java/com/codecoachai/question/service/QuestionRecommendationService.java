package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromGapDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromMatchReportDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromStudyPlanDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationQueryDTO;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchDetailVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchListVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationItemVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationSourceTypeVO;
import java.util.List;

public interface QuestionRecommendationService {

    QuestionRecommendationGenerateVO generateFromGap(QuestionRecommendationGenerateFromGapDTO dto);

    QuestionRecommendationGenerateVO generateFromMatchReport(QuestionRecommendationGenerateFromMatchReportDTO dto);

    QuestionRecommendationGenerateVO generateFromStudyPlan(QuestionRecommendationGenerateFromStudyPlanDTO dto);

    List<QuestionRecommendationSourceTypeVO> sourceTypes();

    PageResult<QuestionRecommendationBatchListVO> listBatches(QuestionRecommendationQueryDTO query);

    QuestionRecommendationBatchDetailVO batchDetail(Long batchId);

    List<QuestionRecommendationItemVO> batchItems(Long batchId);
}
