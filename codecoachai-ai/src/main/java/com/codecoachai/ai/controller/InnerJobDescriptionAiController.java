package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.ParseJobDescriptionDTO;
import com.codecoachai.ai.domain.vo.ParseJobDescriptionVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/job-descriptions")
public class InnerJobDescriptionAiController {

    private final AiService aiService;

    @PostMapping("/parse")
    public Result<ParseJobDescriptionVO> parse(@RequestBody ParseJobDescriptionDTO dto) {
        return Result.success(aiService.parseJobDescription(dto));
    }
}
