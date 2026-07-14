package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.ResumeSuggestionCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionBatchAcceptDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionDecisionDTO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionVO;
import java.util.List;

public interface ResumeSuggestionService {
    ResumeSuggestionVO create(ResumeSuggestionCreateDTO dto);

    List<ResumeSuggestionVO> list(Long resumeId, String status);

    ResumeSuggestionVO detail(Long suggestionId);

    ResumeSuggestionVO decide(Long suggestionId, ResumeSuggestionDecisionDTO dto);

    List<ResumeSuggestionVO> acceptLowRiskBatch(ResumeSuggestionBatchAcceptDTO dto);
}
