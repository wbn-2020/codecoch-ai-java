package com.codecoachai.question.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.question.feign.vo.AgentTaskVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai", contextId = "agentBusinessActionFeignClient")
public interface AgentBusinessActionFeignClient {

    @PostMapping("/inner/agent/job-coach/business-actions/complete")
    Result<AgentTaskVO> completeBusinessAction(@RequestBody AgentBusinessActionCompleteDTO dto);
}
