package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/interview")
public class InnerAiController {

    private final AiService aiService;

    @PostMapping("/question")
    public Result<GenerateInterviewQuestionVO> generateQuestion(@RequestBody GenerateInterviewQuestionDTO dto) {
        return Result.success(aiService.generateQuestion(dto));
    }

    @PostMapping("/evaluate")
    public Result<EvaluateAnswerVO> evaluate(@RequestBody EvaluateAnswerDTO dto) {
        return Result.success(aiService.evaluate(dto));
    }

    @PostMapping("/follow-up")
    public Result<GenerateFollowUpVO> followUp(@RequestBody GenerateFollowUpDTO dto) {
        return Result.success(aiService.generateFollowUp(dto));
    }

    @PostMapping("/report")
    public Result<GenerateReportVO> report(@RequestBody GenerateReportDTO dto) {
        return Result.success(aiService.generateReport(dto));
    }
}
