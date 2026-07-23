package com.codecoachai.resume.careerreview;

import com.codecoachai.common.core.domain.Result;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/career-campaigns")
public class InnerCareerCampaignReviewEvidenceController {

    private final CareerCampaignReviewEvidenceService evidenceService;

    @GetMapping("/users/{userId}/campaigns/{campaignId}/review-evidence")
    public Result<CareerCampaignReviewEvidenceVO> get(
            @PathVariable Long userId,
            @PathVariable Long campaignId,
            @RequestParam("dataCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataCutoffAt) {
        return Result.success(evidenceService.get(userId, campaignId, dataCutoffAt));
    }
}
