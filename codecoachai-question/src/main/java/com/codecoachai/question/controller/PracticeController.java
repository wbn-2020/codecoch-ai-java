package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.PracticeRecordQueryDTO;
import com.codecoachai.question.domain.dto.PracticeSubmitDTO;
import com.codecoachai.question.domain.vo.PracticeRecordVO;
import com.codecoachai.question.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Practice Answer Review", description = "User-side short-answer practice AI review APIs.")
public class PracticeController {

    private final PracticeService practiceService;

    @Operation(summary = "Submit short-answer practice for AI review", description = "User endpoint. The backend reads the question and reference answer from the question bank; frontend must not submit referenceAnswer.")
    @PostMapping({"/practice/questions/{questionId}/answers", "/questions/{questionId}/practice", "/questions/{questionId}/answer-review"})
    public Result<PracticeRecordVO> submit(@PathVariable Long questionId,
                                           @Valid @RequestBody PracticeSubmitDTO dto) {
        return Result.success(practiceService.submit(questionId, dto));
    }

    @Operation(summary = "Page current user's answer review records")
    @GetMapping({"/practice/records", "/questions/practice-records"})
    public Result<PageResult<PracticeRecordVO>> list(@ModelAttribute PracticeRecordQueryDTO query) {
        return Result.success(practiceService.list(query));
    }

    @Operation(summary = "Get current user's answer review record detail")
    @GetMapping({"/practice/records/{recordId}", "/questions/practice-records/{recordId}", "/questions/answer-reviews/{recordId}"})
    public Result<PracticeRecordVO> detail(@PathVariable Long recordId) {
        return Result.success(practiceService.detail(recordId));
    }

    @Operation(summary = "Page current user's answer review records for one question")
    @GetMapping("/questions/{questionId}/answer-reviews")
    public Result<PageResult<PracticeRecordVO>> listByQuestion(@PathVariable Long questionId,
                                                               @ModelAttribute PracticeRecordQueryDTO query) {
        PracticeRecordQueryDTO actualQuery = query == null ? new PracticeRecordQueryDTO() : query;
        actualQuery.setQuestionId(questionId);
        return Result.success(practiceService.list(actualQuery));
    }
}
