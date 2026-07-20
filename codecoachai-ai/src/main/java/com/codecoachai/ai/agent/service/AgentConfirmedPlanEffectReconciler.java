package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentConfirmedPlanEffectReconciler {

    private final V4FeatureGate featureGate;
    private final AgentPlanChangeApplyService applyService;

    public void reconcile(Long userId, AgentRun run) {
        if (!featureGate.isAdaptivePlanEnabled()) {
            return;
        }
        applyService.reconcileConfirmedChanges(userId, run);
    }
}
