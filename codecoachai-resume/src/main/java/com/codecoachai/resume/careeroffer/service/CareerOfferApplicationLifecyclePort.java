package com.codecoachai.resume.careeroffer.service;

public interface CareerOfferApplicationLifecyclePort {

    void synchronizeFinalOutcome(Long userId, Long applicationId, String offerStatus,
                                 Integer expectedApplicationLockVersion, String idempotencyKey);
}
