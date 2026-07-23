package com.codecoachai.resume.careercampaign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.CampaignView;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.CompleteRequest;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.SaveRequest;
import com.codecoachai.resume.config.V7FeatureGate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/career-campaigns")
public class CareerCampaignController {

    private final CareerCampaignService service;
    private final V7FeatureGate featureGate;

    @GetMapping
    public Result<List<CampaignView>> list() {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.list());
    }

    @PostMapping
    public Result<CampaignView> create(@RequestBody SaveRequest request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.create(request));
    }

    @GetMapping("/{id}")
    public Result<CampaignView> get(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.get(id));
    }

    @PutMapping("/{id}")
    public Result<CampaignView> update(@PathVariable Long id, @RequestBody SaveRequest request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.update(id, request));
    }

    @PostMapping("/{id}/activate")
    public Result<CampaignView> activate(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.activate(id));
    }

    @PostMapping("/{id}/complete")
    public Result<CampaignView> complete(@PathVariable Long id,
                                         @RequestBody(required = false) CompleteRequest request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.complete(id,
                request != null && Boolean.TRUE.equals(request.getRetainOpenApplications())));
    }

    @PostMapping("/{id}/archive")
    public Result<CampaignView> archive(@PathVariable Long id) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.archive(id));
    }

    @PostMapping("/{id}/applications/{applicationId}")
    public Result<CampaignView> addApplication(@PathVariable Long id, @PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(service.addApplication(id, applicationId));
    }

    @DeleteMapping("/{id}/applications/{applicationId}")
    public Result<Void> removeApplication(@PathVariable Long id, @PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        service.removeApplication(id, applicationId);
        return Result.success();
    }
}
