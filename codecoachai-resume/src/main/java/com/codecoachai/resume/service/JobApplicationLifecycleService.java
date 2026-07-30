package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.entity.JobApplication;
import java.util.Set;

public interface JobApplicationLifecycleService {

    Set<String> allowedTransitions(String status);

    JobApplication transition(Long applicationId, String targetStatus,
                              Integer expectedLockVersion, String idempotencyKey);

    JobApplication transitionForUser(Long userId, Long applicationId, String targetStatus,
                                     Integer expectedLockVersion, String idempotencyKey);

    default JobApplication transition(Long applicationId, String targetStatus,
                                      Integer expectedLockVersion, String idempotencyKey,
                                      String note) {
        return transition(applicationId, targetStatus, expectedLockVersion, idempotencyKey);
    }

    default JobApplication transitionForUser(Long userId, Long applicationId, String targetStatus,
                                             Integer expectedLockVersion, String idempotencyKey,
                                             String note) {
        return transitionForUser(userId, applicationId, targetStatus,
                expectedLockVersion, idempotencyKey);
    }
}
