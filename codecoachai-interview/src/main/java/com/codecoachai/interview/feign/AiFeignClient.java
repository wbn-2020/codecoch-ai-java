package com.codecoachai.interview.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.dto.EvaluateAnswerDTO;
import com.codecoachai.interview.feign.dto.GenerateFollowUpDTO;
import com.codecoachai.interview.feign.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.interview.feign.dto.GenerateLearningPlanDTO;
import com.codecoachai.interview.feign.dto.GenerateReportDTO;
import com.codecoachai.interview.feign.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.interview.feign.vo.EvaluateAnswerVO;
import com.codecoachai.interview.feign.vo.GenerateFollowUpVO;
import com.codecoachai.interview.feign.vo.GenerateInterviewQuestionVO;
import com.codecoachai.interview.feign.vo.GenerateLearningPlanVO;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai")
public interface AiFeignClient {

    @PostMapping("/inner/ai/interview/question")
    Result<GenerateInterviewQuestionVO> generateQuestion(@RequestBody GenerateInterviewQuestionDTO dto);

    @PostMapping("/inner/ai/interview/evaluate")
    Result<EvaluateAnswerVO> evaluate(@RequestBody EvaluateAnswerDTO dto);

    @PostMapping(value = "/inner/ai/interview/evaluate/stream")
    Response evaluateStream(@RequestBody EvaluateAnswerDTO dto);

    @PostMapping("/inner/ai/interview/follow-up")
    Result<GenerateFollowUpVO> followUp(@RequestBody GenerateFollowUpDTO dto);

    @PostMapping("/inner/ai/interview/report")
    Result<GenerateReportVO> report(@RequestBody GenerateReportDTO dto);

    @PostMapping("/inner/ai/learning-plans/generate")
    Result<GenerateLearningPlanVO> generateLearningPlan(@RequestBody GenerateLearningPlanDTO dto);

    @PostMapping("/inner/ai/study-plans/generate-from-gap")
    Result<GenerateLearningPlanVO> generateTargetedStudyPlan(@RequestBody GenerateTargetedStudyPlanDTO dto);
}
