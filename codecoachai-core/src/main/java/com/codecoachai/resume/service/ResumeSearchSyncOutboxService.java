package com.codecoachai.resume.service;

public interface ResumeSearchSyncOutboxService {

    String OP_UPSERT = "UPSERT";
    String OP_DELETE = "DELETE";

    Long enqueue(Long resumeId, Long userId, String operation);

    boolean dispatch(Long outboxId);

    int retryPending(int batchSize);
}
