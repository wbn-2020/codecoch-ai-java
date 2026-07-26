package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.dto.InterviewReplayCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewReplayVO;
import com.codecoachai.interview.service.InterviewReplayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewReplayController {

    private final InterviewReplayService replayService;

    @PostMapping("/interviews/{sourceSessionId}/replays")
    public Result<InterviewReplayVO> create(
            @PathVariable Long sourceSessionId,
            @Valid @RequestBody InterviewReplayCreateDTO dto) {
        return Result.success(replayService.create(sourceSessionId, dto));
    }
}
