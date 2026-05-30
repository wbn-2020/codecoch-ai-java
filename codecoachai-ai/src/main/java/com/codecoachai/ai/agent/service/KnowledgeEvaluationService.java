package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalCaseQueryDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalCaseSaveDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalRunRequestDTO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvalCaseVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvalRunVO;
import com.codecoachai.common.core.domain.PageResult;

public interface KnowledgeEvaluationService {

    PageResult<KnowledgeEvalCaseVO> pageCases(Long userId, KnowledgeEvalCaseQueryDTO query);

    KnowledgeEvalCaseVO saveCase(Long userId, KnowledgeEvalCaseSaveDTO dto);

    void deleteCase(Long userId, Long id);

    KnowledgeEvalRunVO run(Long userId, KnowledgeEvalRunRequestDTO dto);

    PageResult<KnowledgeEvalRunVO> pageRuns(Long userId, Long pageNo, Long pageSize);

    KnowledgeEvalRunVO getRun(Long userId, Long runId);
}
