package com.codecoachai.interview.audioretention;

public interface AudioRetentionService {

    AudioRetentionVO register(Long sessionId, AudioRetentionRegisterDTO dto);

    AudioRetentionVO get(Long sessionId, Long retentionRecordId);

    AudioCleanupTaskVO requestCleanup(Long sessionId, Long retentionRecordId, AudioCleanupRequestDTO dto);

    AudioCleanupTaskVO getCleanupTask(Long sessionId, String cleanupTaskId);

    AudioCleanupTaskVO cancelCleanup(Long sessionId, String cleanupTaskId);
}
