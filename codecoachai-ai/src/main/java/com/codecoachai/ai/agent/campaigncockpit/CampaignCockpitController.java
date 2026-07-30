package com.codecoachai.ai.agent.campaigncockpit;

import com.codecoachai.ai.agent.campaigncockpit.CampaignActionDecisionModels.Request;
import com.codecoachai.ai.agent.campaigncockpit.CampaignActionDecisionModels.View;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ScenarioPreview;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ScenarioRequest;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/career-campaign-cockpits")
public class CampaignCockpitController {

    private final CampaignCockpitService cockpitService;
    private final CampaignActionDecisionService actionDecisionService;
    private final V8FeatureGate featureGate;

    @GetMapping("/{campaignId}")
    public Result<CockpitView> get(@PathVariable Long campaignId) {
        featureGate.requireCampaignCockpit();
        return Result.success(cockpitService.get(SecurityAssert.requireLoginUserId(), campaignId));
    }

    @PostMapping("/{campaignId}/action-decisions")
    public Result<View> decide(
            @PathVariable Long campaignId,
            @Valid @RequestBody Request request) {
        featureGate.requireCampaignCockpit();
        return Result.success(actionDecisionService.decide(
                SecurityAssert.requireLoginUserId(), campaignId, request));
    }

    @GetMapping("/{campaignId}/action-decisions")
    public Result<List<View>> decisions(@PathVariable Long campaignId) {
        featureGate.requireCampaignCockpit();
        return Result.success(actionDecisionService.list(
                SecurityAssert.requireLoginUserId(), campaignId));
    }

    @PostMapping("/{campaignId}/scenarios/preview")
    public Result<ScenarioPreview> previewScenario(
            @PathVariable Long campaignId,
            @Valid @RequestBody ScenarioRequest request) {
        featureGate.requireCampaignPortfolio();
        return Result.success(cockpitService.previewScenario(
                SecurityAssert.requireLoginUserId(), campaignId, request));
    }
}
