package com.codecoachai.interview.audioretention;

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
@RequestMapping("/interviews/{sessionId}/audio-retention")
public class AudioRetentionController {

    private final AudioRetentionService service;

    @PostMapping
    public Result<AudioRetentionVO> register(@PathVariable Long sessionId,
                                             @Valid @RequestBody AudioRetentionRegisterDTO dto) {
        return Result.success(service.register(sessionId, dto));
    }

    @GetMapping("/{retentionRecordId}")
    public Result<AudioRetentionVO> get(@PathVariable Long sessionId,
                                        @PathVariable Long retentionRecordId) {
        return Result.success(service.get(sessionId, retentionRecordId));
    }

    @PostMapping("/{retentionRecordId}/cleanup-tasks")
    public Result<AudioCleanupTaskVO> requestCleanup(@PathVariable Long sessionId,
                                                     @PathVariable Long retentionRecordId,
                                                     @Valid @RequestBody AudioCleanupRequestDTO dto) {
        return Result.success(service.requestCleanup(sessionId, retentionRecordId, dto));
    }

    @GetMapping("/cleanup-tasks/{cleanupTaskId}")
    public Result<AudioCleanupTaskVO> getCleanupTask(@PathVariable Long sessionId,
                                                     @PathVariable String cleanupTaskId) {
        return Result.success(service.getCleanupTask(sessionId, cleanupTaskId));
    }

    @DeleteMapping("/cleanup-tasks/{cleanupTaskId}")
    public Result<AudioCleanupTaskVO> cancelCleanup(@PathVariable Long sessionId,
                                                    @PathVariable String cleanupTaskId) {
        return Result.success(service.cancelCleanup(sessionId, cleanupTaskId));
    }
}
