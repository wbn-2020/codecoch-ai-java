package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Computation;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Narrative;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class RuleOnlyCampaignPulseNarrativeEnhancer implements CampaignPulseNarrativeEnhancer {

    @Override
    public Narrative enhance(Long userId, Long campaignId, Computation computation) {
        Narrative result = new Narrative();
        result.setSummary(summary(computation));
        result.setFacts(new ArrayList<>(computation.getFacts().entrySet().stream()
                .limit(8)
                .map(entry -> entry.getKey() + "：" + entry.getValue())
                .toList()));
        result.setChanges(new ArrayList<>(computation.getChanges()));
        result.setDriftReasons(new ArrayList<>(computation.getDriftSignals()));
        result.setFocusAreas(computation.getActionSeeds().stream()
                .limit(5)
                .map(item -> item.getTitle())
                .toList());
        result.setActionSelections(computation.getActionSeeds().stream()
                .limit(8)
                .map(item -> item.getSemanticKey())
                .toList());
        result.setLimits(new ArrayList<>(computation.getLimits()));
        result.setConfidenceLevel(computation.getConfidenceLevel());
        result.setFallback(false);
        return result;
    }

    private String summary(Computation computation) {
        if (computation.getActionSeeds().isEmpty()) {
            return "当前周期没有需要进入统一行动队列的明确事项。";
        }
        String sample = "LOW".equalsIgnoreCase(computation.getConfidenceLevel())
                ? "当前为低样本结果，仅用于描述事实和弱信号。" : "";
        return "当前周期共有 " + computation.getActionSeeds().size()
                + " 项规则行动候选。" + sample;
    }
}
