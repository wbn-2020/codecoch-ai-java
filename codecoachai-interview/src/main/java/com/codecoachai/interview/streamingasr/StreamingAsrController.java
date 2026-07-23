package com.codecoachai.interview.streamingasr;

import com.codecoachai.common.core.domain.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interview-streaming-asr/sessions")
public class StreamingAsrController {

    private final StreamingAsrSessionService service;

    @PostMapping
    public Result<StreamingAsrSessionVO> open(@Valid @RequestBody StreamingAsrSessionCreateDTO dto) {
        return Result.success(service.open(dto));
    }

    @PostMapping("/{sessionId}/chunks")
    public Result<StreamingAsrSessionVO> accept(@PathVariable String sessionId,
                                                @Valid @RequestBody StreamingAsrChunkDTO dto) {
        return Result.success(service.accept(sessionId, dto));
    }

    @PostMapping("/{sessionId}/complete")
    public Result<StreamingAsrSessionVO> complete(@PathVariable String sessionId) {
        return Result.success(service.complete(sessionId));
    }

    @GetMapping("/{sessionId}")
    public Result<StreamingAsrSessionVO> get(@PathVariable String sessionId) {
        return Result.success(service.get(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public Result<StreamingAsrSessionVO> cancel(@PathVariable String sessionId) {
        return Result.success(service.cancel(sessionId));
    }
}
