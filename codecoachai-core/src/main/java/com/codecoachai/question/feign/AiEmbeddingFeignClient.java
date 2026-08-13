package com.codecoachai.question.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.dto.EmbeddingRequestDTO;
import com.codecoachai.question.feign.vo.EmbeddingResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai", contextId = "aiEmbeddingFeignClient", path = "/inner/ai")
public interface AiEmbeddingFeignClient {

    @PostMapping("/embeddings")
    Result<EmbeddingResponseVO> embeddings(@RequestBody EmbeddingRequestDTO dto);
}
