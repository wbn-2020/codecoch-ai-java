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

    QuestionRecommendationGenerateVO submitFromGap(QuestionRecommendationGenerateFromGapDTO dto);

    QuestionRecommendationGenerateVO submitFromMatchReport(QuestionRecommendationGenerateFromMatchReportDTO dto);

    QuestionRecommendationGenerateVO submitFromStudyPlan(QuestionRecommendationGenerateFromStudyPlanDTO dto);

    QuestionRecommendationGenerateVO executeBatch(Long batchId, Long userId);

    List<QuestionRecommendationSourceTypeVO> sourceTypes();

    PageResult<QuestionRecommendationBatchListVO> listBatches(QuestionRecommendationQueryDTO query);

    QuestionRecommendationBatchDetailVO batchDetail(Long batchId);

    List<QuestionRecommendationItemVO> batchItems(Long batchId);

    List<QuestionRecommendationItemVO> recommendByJobTarget(Long targetJobId, Integer limit);

    List<QuestionRecommendationItemVO> recommendBySkill(Long skillProfileId, String skillCode, String skillName, Integer limit);

    /**
     * 按 JD 关键词做规则版冷启动推荐：不依赖能力画像、匹配报告、学习计划或历史批次，
     * 直接用 JD 文本命中 question_knowledge_keyword 后从正式题库取题。
     *
     * @param targetJobId 目标岗位（当前仅用于透传来源，可为空）
     * @param jdText      JD 原文或岗位关键词文本（可为空，为空时退化为按权重取高频题）
     * @param limit       返回题量
     */
    List<QuestionRecommendationItemVO> recommendByJd(Long targetJobId, String jdText, Integer limit);
}
