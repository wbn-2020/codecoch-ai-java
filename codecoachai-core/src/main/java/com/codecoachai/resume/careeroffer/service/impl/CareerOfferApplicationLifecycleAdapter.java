package com.codecoachai.resume.careeroffer.service.impl;

import com.codecoachai.resume.careeroffer.service.CareerOfferApplicationLifecyclePort;
import com.codecoachai.resume.service.JobApplicationLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CareerOfferApplicationLifecycleAdapter implements CareerOfferApplicationLifecyclePort {

    private final JobApplicationLifecycleService lifecycleService;

    @Override
    public void synchronizeFinalOutcome(Long userId, Long applicationId, String offerStatus,
                                        Integer expectedApplicationLockVersion, String idempotencyKey) {
        String targetStatus = switch (offerStatus) {
            case "ACCEPTED" -> "ACCEPTED";
            case "DECLINED" -> "REJECTED";
            case "WITHDRAWN" -> "WITHDRAWN";
            case "EXPIRED" -> "CLOSED";
            default -> throw new IllegalArgumentException("Offer status is not final: " + offerStatus);
        };
        lifecycleService.transitionForUser(userId, applicationId, targetStatus,
                expectedApplicationLockVersion, idempotencyKey);
    }
}
