package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.EmbeddingRequestDTO;
import com.codecoachai.ai.domain.vo.EmbeddingResponseVO;

public interface EmbeddingService {

    EmbeddingResponseVO embed(EmbeddingRequestDTO dto);
}
