package com.codecoachai.resume.careeroffer.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.careeroffer.dto.CareerOfferCreateDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionConfirmDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionPreviewDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferTransitionDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferVersionCreateDTO;
import com.codecoachai.resume.careeroffer.service.CareerOfferService;
import com.codecoachai.resume.careeroffer.vo.CareerOfferDecisionVO;
import com.codecoachai.resume.careeroffer.vo.CareerOfferVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class CareerOfferController {

    private final CareerOfferService service;
    private final V7FeatureGate featureGate;

    @GetMapping("/applications/{applicationId}/offers")
    public Result<List<CareerOfferVO>> list(@PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireOffer();
        return Result.success(service.listByApplication(applicationId));
    }

    @PostMapping("/applications/{applicationId}/offers")
    public Result<CareerOfferVO> create(@PathVariable Long applicationId,
                                        @Valid @RequestBody CareerOfferCreateDTO request,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireOffer();
        return Result.success(service.create(applicationId, request, idempotencyKey));
    }

    @PostMapping("/offers/{offerId}/versions")
    public Result<CareerOfferVO> createVersion(@PathVariable Long offerId,
                                               @Valid @RequestBody CareerOfferVersionCreateDTO request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireOffer();
        return Result.success(service.createVersion(offerId, request, idempotencyKey));
    }

    @PostMapping("/offers/{offerId}/transitions")
    public Result<CareerOfferVO> transition(@PathVariable Long offerId,
                                            @Valid @RequestBody CareerOfferTransitionDTO request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireOffer();
        return Result.success(service.transition(offerId, request, idempotencyKey));
    }

    @PostMapping("/career-campaigns/{campaignId}/offer-decisions/preview")
    public Result<CareerOfferDecisionVO> preview(@PathVariable Long campaignId,
                                                 @RequestBody(required = false)
                                                 CareerOfferDecisionPreviewDTO request,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireOffer();
        return Result.success(service.previewDecision(campaignId, request, idempotencyKey));
    }

    @PostMapping("/career-campaigns/{campaignId}/offer-decisions/{decisionId}/confirm")
    public Result<CareerOfferDecisionVO> confirm(@PathVariable Long campaignId,
                                                @PathVariable Long decisionId,
                                                @Valid @RequestBody CareerOfferDecisionConfirmDTO request,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireOffer();
        return Result.success(service.confirmDecision(campaignId, decisionId, request, idempotencyKey));
    }
}
