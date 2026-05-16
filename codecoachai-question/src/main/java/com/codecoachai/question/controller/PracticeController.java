package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.PracticeRecordQueryDTO;
import com.codecoachai.question.domain.dto.PracticeSubmitDTO;
import com.codecoachai.question.domain.vo.PracticeRecordVO;
import com.codecoachai.question.service.PracticeService;
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
public class PracticeController {

    private final PracticeService practiceService;

    @PostMapping({"/practice/questions/{questionId}/answers", "/questions/{questionId}/practice"})
    public Result<PracticeRecordVO> submit(@PathVariable Long questionId,
                                           @Valid @RequestBody PracticeSubmitDTO dto) {
        return Result.success(practiceService.submit(questionId, dto));
    }

    @GetMapping({"/practice/records", "/questions/practice-records"})
    public Result<PageResult<PracticeRecordVO>> list(@ModelAttribute PracticeRecordQueryDTO query) {
        return Result.success(practiceService.list(query));
    }

    @GetMapping({"/practice/records/{recordId}", "/questions/practice-records/{recordId}"})
    public Result<PracticeRecordVO> detail(@PathVariable Long recordId) {
        return Result.success(practiceService.detail(recordId));
    }
}
