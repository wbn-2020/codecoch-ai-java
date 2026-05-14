package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.AdminQuestionSaveDTO;
import com.codecoachai.question.domain.dto.QuestionQueryDTO;
import com.codecoachai.question.domain.dto.UpdateStatusDTO;
import com.codecoachai.question.domain.vo.QuestionDetailVO;
import com.codecoachai.question.domain.vo.QuestionListVO;
import com.codecoachai.question.service.QuestionService;
import com.codecoachai.common.security.util.SecurityAssert;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminQuestionController {

    private final QuestionService questionService;

    @GetMapping("/admin/questions")
    public Result<PageResult<QuestionListVO>> pageQuestions(QuestionQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(questionService.pageAdminQuestions(query));
    }

    @GetMapping("/admin/questions/page")
    public Result<PageResult<QuestionListVO>> pageQuestionsAlias(QuestionQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(questionService.pageAdminQuestions(query));
    }

    @GetMapping("/admin/questions/{id}")
    public Result<QuestionDetailVO> getQuestion(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(questionService.getQuestion(id));
    }

    @PostMapping("/admin/questions")
    public Result<QuestionDetailVO> createQuestion(@Valid @RequestBody AdminQuestionSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(questionService.createQuestion(dto));
    }

    @PutMapping("/admin/questions/{id}")
    public Result<QuestionDetailVO> updateQuestion(@PathVariable Long id,
                                                   @Valid @RequestBody AdminQuestionSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(questionService.updateQuestion(id, dto));
    }

    @DeleteMapping("/admin/questions/{id}")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        questionService.deleteQuestion(id);
        return Result.success();
    }

    @PutMapping("/admin/questions/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusDTO dto) {
        SecurityAssert.requireAdmin();
        questionService.updateStatus(id, dto);
        return Result.success();
    }
}
