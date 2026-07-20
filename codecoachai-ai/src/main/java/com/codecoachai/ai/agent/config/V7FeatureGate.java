package com.codecoachai.ai.agent.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class V7FeatureGate {

    @Value("${codecoachai.features.v7.external-plan-source:false}")
    private boolean externalPlanSource;

    @Value("${codecoachai.features.v7.campaign-review:false}")
    private boolean campaignReview;

    public void requireExternalPlanSource() {
        require(externalPlanSource, "当前版本未开启外部计划来源功能。");
    }

    public void requireCampaignReview() {
        require(campaignReview, "当前版本未开启求职周期复盘功能。");
    }

    private void require(boolean enabled, String message) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
