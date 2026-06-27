package com.codecoachai.task.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.feign.dto.AgentRunFailureDTO;
import com.codecoachai.task.feign.dto.ExecuteAgentDailyPlanDTO;
import com.codecoachai.task.feign.dto.GenerateQuestionDraftDTO;
import com.codecoachai.task.feign.dto.GenerateReportDTO;
import com.codecoachai.task.feign.dto.ParseResumeDTO;
import com.codecoachai.task.feign.vo.AgentDailyPlanVO;
import com.codecoachai.task.feign.vo.GenerateQuestionDraftVO;
import com.codecoachai.task.feign.vo.GenerateReportVO;
import com.codecoachai.task.feign.vo.ParseResumeVO;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调用 ai-service 的 inner 接口（统一入口）。
 *
 * 异步消费者会通过此客户端把任务真正交给 ai-service 处理。
 */
@FeignClient(name = "codecoachai-ai", contextId = "taskAiFeignClient")
public interface AiFeignClient {

    @PostMapping("/inner/ai/resume/parse")
    Result<ParseResumeVO> parseResume(@RequestBody ParseResumeDTO dto);

    @PostMapping("/inner/ai/interview/report")
    Result<GenerateReportVO> generateInterviewReport(@RequestBody GenerateReportDTO dto);

    @PostMapping("/inner/ai/questions/generate")
    Result<GenerateQuestionDraftVO> generateQuestionDrafts(@RequestBody GenerateQuestionDraftDTO dto);

    @PostMapping("/inner/agent/job-coach/daily-plan/runs/{runId}/execute")
    Result<AgentDailyPlanVO> executeAgentDailyPlan(@PathVariable("runId") Long runId,
                                                   @RequestBody ExecuteAgentDailyPlanDTO dto);

    @PostMapping("/inner/agent/job-coach/daily-plan/runs/{runId}/fail")
    Result<AgentDailyPlanVO> failAgentDailyPlan(@PathVariable("runId") Long runId,
                                                @RequestBody AgentRunFailureDTO dto);

    @GetMapping("/inner/agent/reminders/candidates")
    Result<List<AgentReminderCandidateVO>> listReminderCandidates(@RequestParam("userId") Long userId,
                                                                  @RequestParam(value = "planDate", required = false)
                                                                  LocalDate planDate);

    @Data
    class AgentReminderCandidateVO {
        private String type;
        private String bizType;
        private String bizId;
        private String title;
        private String content;
        private String actionUrl;
        private String fallbackPath;
        private String fallbackLabel;
        private LocalDate planDate;
    }
}
