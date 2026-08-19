package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.PracticeRecordQueryDTO;
import com.codecoachai.question.domain.dto.PracticeSubmitDTO;
import com.codecoachai.question.domain.vo.PracticeRecordVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationItemVO;

public interface PracticeService {

    PracticeRecordVO submit(Long questionId, PracticeSubmitDTO dto);

    QuestionRecommendationItemVO recommendationQuestion(Long recommendationItemId);

    PracticeRecordVO submitRecommendation(Long recommendationItemId, PracticeSubmitDTO dto);

    PageResult<PracticeRecordVO> list(PracticeRecordQueryDTO query);

    PracticeRecordVO detail(Long recordId);
}
