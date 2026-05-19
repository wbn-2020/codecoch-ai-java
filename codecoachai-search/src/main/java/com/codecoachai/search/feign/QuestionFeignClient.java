package com.codecoachai.search.feign;

import com.codecoachai.common.core.domain.Result;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 题目服务 Feign 客户端（搜索同步用）。
 */
@FeignClient(name = "codecoachai-question", contextId = "searchQuestionFeign")
public interface QuestionFeignClient {

    @GetMapping("/inner/questions/{id}/search-doc")
    Result<Map<String, Object>> getSearchDoc(@PathVariable("id") Long id);
}
