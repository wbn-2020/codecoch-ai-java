package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalCaseQueryDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalCaseSaveDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalRunRequestDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateThresholdSweepDTO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvalCaseVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvalRunVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateThresholdSweepVO;

public interface QuestionDuplicateEvaluationService {

    PageResult<QuestionDuplicateEvalCaseVO> pageCases(QuestionDuplicateEvalCaseQueryDTO query);

    QuestionDuplicateEvalCaseVO saveCase(QuestionDuplicateEvalCaseSaveDTO dto);

    void deleteCase(Long id);

    QuestionDuplicateEvalRunVO run(QuestionDuplicateEvalRunRequestDTO dto);

    QuestionDuplicateThresholdSweepVO thresholdSweep(QuestionDuplicateThresholdSweepDTO dto);

    PageResult<QuestionDuplicateEvalRunVO> pageRuns(Long pageNo, Long pageSize);

    QuestionDuplicateEvalRunVO getRun(Long runId);
}
