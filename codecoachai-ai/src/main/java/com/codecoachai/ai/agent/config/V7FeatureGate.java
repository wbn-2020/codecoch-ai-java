package com.codecoachai.ai.agent.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "codecoachai.features.v7")
public class V7FeatureGate {

    private boolean externalPlanSource;

    private boolean campaignReview;

    public void setExternalPlanSource(boolean externalPlanSource) {
        this.externalPlanSource = externalPlanSource;
    }

    public void setCampaignReview(boolean campaignReview) {
        this.campaignReview = campaignReview;
    }

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
