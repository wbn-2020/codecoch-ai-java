package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.dto.InterviewComparisonCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewComparisonVO;
import com.codecoachai.interview.service.InterviewComparisonService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interview-comparisons")
public class InterviewComparisonController {

    private final InterviewComparisonService comparisonService;

    @PostMapping
    public Result<InterviewComparisonVO> compare(@Valid @RequestBody InterviewComparisonCreateDTO dto) {
        return Result.success(comparisonService.compare(dto));
    }

    @GetMapping
    public Result<List<InterviewComparisonVO>> list(
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(comparisonService.list(limit));
    }

    @GetMapping("/{id}")
    public Result<InterviewComparisonVO> detail(@PathVariable Long id) {
        return Result.success(comparisonService.detail(id));
    }
}
