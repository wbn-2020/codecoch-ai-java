package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.dto.InterviewRemediationCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewRemediationOptionsVO;
import com.codecoachai.interview.domain.vo.InterviewRemediationVO;
import com.codecoachai.interview.service.InterviewRemediationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewRemediationController {

    private final InterviewRemediationService remediationService;

    @PostMapping("/interview-remediations")
    public Result<InterviewRemediationVO> create(@Valid @RequestBody InterviewRemediationCreateDTO dto) {
        return Result.success(remediationService.create(dto));
    }

    @GetMapping("/interviews/{id}/remediation-options")
    public Result<InterviewRemediationOptionsVO> options(@PathVariable Long id) {
        return Result.success(remediationService.options(id));
    }
}
