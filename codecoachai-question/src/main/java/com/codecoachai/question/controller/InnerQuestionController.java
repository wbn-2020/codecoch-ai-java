package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.InnerSelectQuestionDTO;
import com.codecoachai.question.domain.dto.RecommendQuestionDTO;
import com.codecoachai.question.domain.vo.InnerQuestionVO;
import com.codecoachai.question.service.QuestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/questions")
public class InnerQuestionController {

    private final QuestionService questionService;

    @PostMapping("/select")
    public Result<InnerQuestionVO> select(@RequestBody InnerSelectQuestionDTO dto) {
        return Result.success(questionService.selectForInterview(dto));
    }

    @PostMapping("/pick-for-interview")
    public Result<InnerQuestionVO> pickForInterview(@RequestBody InnerSelectQuestionDTO dto) {
        return Result.success(questionService.selectForInterview(dto));
    }

    @GetMapping("/{id}")
    public Result<InnerQuestionVO> getQuestion(@PathVariable Long id) {
        return Result.success(questionService.getInnerQuestion(id));
    }

    @GetMapping("/recommend")
    public Result<List<InnerQuestionVO>> recommendByGet() {
        return Result.success(questionService.recommend(new RecommendQuestionDTO()));
    }

    @PostMapping("/recommend-for-report")
    public Result<List<InnerQuestionVO>> recommend(@RequestBody RecommendQuestionDTO dto) {
        return Result.success(questionService.recommend(dto));
    }
}
