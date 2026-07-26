package com.codecoachai.resume.careerreview;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal review-evidence endpoint feeding the AI service's campaign review generation.
 *
 * <p>Auth is handled transparently by {@code InternalCallFilter} (V2 HMAC over {@code /inner/**},
 * covering method, path, query, body hash and a replay nonce). The caller's
 * {@code X-Service-Name} header participates in that signature, so it cannot be forged by any
 * party without the shared secret.
 *
 * <p>Because this endpoint exposes a user's full job-search evidence for an arbitrary
 * {@code userId}, passing the HMAC check is not enough: any service in the trusted set could
 * otherwise read it. Access is therefore additionally pinned to the AI service — the only
 * legitimate consumer ({@code CareerCampaignReviewEvidenceFeignClient}).
 *
 * <p>The supplied {@code dataCutoffAt} is treated as advisory; the service recomputes the
 * authoritative cutoff itself.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/career-campaigns")
public class InnerCareerCampaignReviewEvidenceController {

    private static final String AI_SERVICE_NAME = "codecoachai-ai";

    private final CareerCampaignReviewEvidenceService evidenceService;

    @GetMapping("/users/{userId}/campaigns/{campaignId}/review-evidence")
    public Result<CareerCampaignReviewEvidenceVO> get(
            @RequestHeader(value = HeaderConstants.SERVICE_NAME, required = false)
            String serviceName,
            @PathVariable Long userId,
            @PathVariable Long campaignId,
            @RequestParam("dataCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataCutoffAt,
            @RequestParam(value = "applicationLimit", required = false) Integer applicationLimit,
            @RequestParam(value = "eventLimitPerSection", required = false)
            Integer eventLimitPerSection) {
        requireAiService(serviceName);
        return Result.success(evidenceService.get(
                userId, campaignId, dataCutoffAt, applicationLimit, eventLimitPerSection));
    }

    private void requireAiService(String serviceName) {
        if (!AI_SERVICE_NAME.equals(serviceName)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Campaign review evidence inner API only accepts the AI service");
        }
    }
}
