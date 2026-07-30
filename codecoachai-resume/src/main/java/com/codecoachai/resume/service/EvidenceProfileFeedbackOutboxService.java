package com.codecoachai.resume.service;

public interface EvidenceProfileFeedbackOutboxService {

    Long enqueue(Long resultId, Long userId, Integer snapshotVersion);

    int requeueAbilityProjectionForProject(Long userId, Long projectEvidenceId);

    boolean dispatch(Long outboxId);

    int retryPending(int batchSize);
}
