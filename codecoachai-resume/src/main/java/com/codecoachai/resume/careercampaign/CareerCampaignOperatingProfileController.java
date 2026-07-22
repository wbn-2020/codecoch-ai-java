package com.codecoachai.resume.careercampaign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels.OperatingProfileView;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels.SaveRequest;
import com.codecoachai.resume.config.V8FeatureGate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/career-campaigns/{campaignId}/operating-profile")
public class CareerCampaignOperatingProfileController {

    private final CareerCampaignOperatingProfileService service;
    private final V8FeatureGate featureGate;

    @GetMapping
    public Result<OperatingProfileView> get(@PathVariable Long campaignId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignCockpit();
        return Result.success(service.get(campaignId));
    }

    @PutMapping
    public Result<OperatingProfileView> save(@PathVariable Long campaignId,
                                             @RequestBody SaveRequest request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignCockpit();
        return Result.success(service.save(campaignId, request));
    }
}
