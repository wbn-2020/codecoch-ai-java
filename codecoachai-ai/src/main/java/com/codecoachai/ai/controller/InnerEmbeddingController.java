package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.EmbeddingRequestDTO;
import com.codecoachai.ai.domain.vo.EmbeddingResponseVO;
import com.codecoachai.ai.service.EmbeddingService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai")
public class InnerEmbeddingController {

    private final EmbeddingService embeddingService;

    @PostMapping("/embeddings")
    public Result<EmbeddingResponseVO> embeddings(@RequestBody EmbeddingRequestDTO dto) {
        return Result.success(embeddingService.embed(dto));
    }
}
