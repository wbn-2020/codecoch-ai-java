package com.codecoachai.task.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.feign.dto.CompleteInterviewReportDTO;
import com.codecoachai.task.feign.vo.InterviewReportContextVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用 interview-service inner 接口。
 *
 * 异步消费者使用：
 *  - 拉取面试上下文（题目 + 回答 + 简历）
 *  - 回写报告
 */
@FeignClient(name = "codecoachai-interview", contextId = "taskInterviewFeignClient")
public interface InterviewFeignClient {

    @GetMapping("/inner/interviews/{sessionId}/report-context")
    Result<InterviewReportContextVO> getReportContext(@PathVariable("sessionId") Long sessionId);

    @PostMapping("/inner/interviews/{sessionId}/complete-report")
    Result<Void> completeReport(@PathVariable("sessionId") Long sessionId,
                                @RequestBody CompleteInterviewReportDTO dto);
}
