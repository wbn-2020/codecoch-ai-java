package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.QuestionQueryDTO;
import com.codecoachai.question.domain.dto.SubmitQuestionAnswerDTO;
import com.codecoachai.question.domain.dto.UpdateMasteryDTO;
import com.codecoachai.question.domain.vo.QuestionDetailVO;
import com.codecoachai.question.domain.vo.QuestionListVO;
import com.codecoachai.question.domain.vo.SubmitQuestionAnswerVO;
import com.codecoachai.question.domain.vo.WrongQuestionVO;
import com.codecoachai.question.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/questions")
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping
    public Result<PageResult<QuestionListVO>> pageQuestions(QuestionQueryDTO query) {
        return Result.success(questionService.pageQuestions(query));
    }

    @GetMapping("/{id}")
    public Result<QuestionDetailVO> getQuestion(@PathVariable Long id) {
        return Result.success(questionService.getQuestion(id));
    }

    @PostMapping("/{id}/answers")
    public Result<SubmitQuestionAnswerVO> submitAnswer(@PathVariable Long id,
                                                       @Valid @RequestBody SubmitQuestionAnswerDTO dto) {
        return Result.success(questionService.submitAnswer(id, dto));
    }

    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id) {
        questionService.favorite(id);
        return Result.success();
    }

    @DeleteMapping("/{id}/favorite")
    public Result<Void> cancelFavorite(@PathVariable Long id) {
        questionService.cancelFavorite(id);
        return Result.success();
    }

    @GetMapping("/favorites")
    public Result<PageResult<QuestionListVO>> pageFavorites(QuestionQueryDTO query) {
        return Result.success(questionService.pageFavorites(query));
    }

    @GetMapping("/wrong-records")
    public Result<PageResult<WrongQuestionVO>> pageWrongRecords(QuestionQueryDTO query) {
        return Result.success(questionService.pageWrongRecords(query));
    }

    @PutMapping("/{id}/mastery")
    public Result<Void> updateMastery(@PathVariable Long id, @Valid @RequestBody UpdateMasteryDTO dto) {
        questionService.updateMastery(id, dto);
        return Result.success();
    }
}
