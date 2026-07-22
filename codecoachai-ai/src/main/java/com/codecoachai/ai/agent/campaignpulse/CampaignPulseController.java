package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaigncockpit.V8FeatureGate;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.GenerateRequest;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.HistoryView;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PlanPreviewRequest;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PulseView;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangePreviewVO;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/career-campaign-pulses")
public class CampaignPulseController {

    private final CampaignPulseService service;
    private final V8FeatureGate featureGate;

    @PostMapping("/generate")
    public Result<PulseView> generate(@Valid @RequestBody GenerateRequest request) {
        featureGate.requireCampaignPulse();
        return Result.success(service.generate(SecurityAssert.requireLoginUserId(), request));
    }

    @GetMapping("/campaigns/{campaignId}")
    public Result<PulseView> current(@PathVariable Long campaignId) {
        featureGate.requireCampaignPulse();
        return Result.success(service.current(SecurityAssert.requireLoginUserId(), campaignId));
    }

    @GetMapping("/campaigns/{campaignId}/history")
    public Result<HistoryView> history(@PathVariable Long campaignId) {
        featureGate.requireCampaignPulse();
        return Result.success(service.history(SecurityAssert.requireLoginUserId(), campaignId));
    }

    @GetMapping("/snapshots/{snapshotId}")
    public Result<PulseView> snapshot(@PathVariable Long snapshotId) {
        featureGate.requireCampaignPulse();
        return Result.success(service.snapshot(SecurityAssert.requireLoginUserId(), snapshotId));
    }

    @PostMapping("/{snapshotId}/plan-preview")
    public Result<AgentPlanChangePreviewVO> previewPlan(
            @PathVariable Long snapshotId,
            @Valid @RequestBody PlanPreviewRequest request) {
        featureGate.requireCampaignPlan();
        return Result.success(service.previewPlan(
                SecurityAssert.requireLoginUserId(), snapshotId, request));
    }
}
