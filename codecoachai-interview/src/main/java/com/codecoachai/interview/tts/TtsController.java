package com.codecoachai.interview.tts;

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
@RequestMapping("/interview-tts/tasks")
public class TtsController {

    private final TtsTaskService service;

    @PostMapping
    public Result<TtsTaskVO> create(@Valid @RequestBody TtsTaskCreateDTO dto) {
        return Result.success(service.create(dto));
    }

    @GetMapping("/{taskId}")
    public Result<TtsTaskVO> get(@PathVariable String taskId) {
        return Result.success(service.get(taskId));
    }

    @DeleteMapping("/{taskId}")
    public Result<TtsTaskVO> cancel(@PathVariable String taskId) {
        return Result.success(service.cancel(taskId));
    }
}
