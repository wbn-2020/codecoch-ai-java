package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.AiResultFeedbackCreateDTO;
import com.codecoachai.ai.domain.vo.AiResultFeedbackStatsVO;
import com.codecoachai.ai.domain.vo.AiResultFeedbackVO;

public interface AiResultFeedbackService {

    AiResultFeedbackVO create(Long userId, AiResultFeedbackCreateDTO dto);

    AiResultFeedbackStatsVO stats(Integer days);
}
