package com.codecoachai.ai.agent.campaigncockpit;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "codecoachai.features.v8")
public class V8FeatureGate {

    private boolean campaignCockpit;
    private boolean campaignPulse;
    private boolean campaignPlan;
    private boolean campaignPortfolio;
    private boolean campaignExport;

    public void requireCampaignCockpit() {
        require(campaignCockpit, "当前版本未开启求职周期驾驶舱功能。");
    }

    public void requireCampaignPulse() {
        require(campaignPulse, "当前版本未开启求职周期脉搏功能。");
    }

    public void requireCampaignPlan() {
        require(campaignPlan, "当前版本未开启求职周期计划回流功能。");
    }

    public void requireCampaignPortfolio() {
        require(campaignPortfolio, "当前版本未开启求职周期情景预览功能。");
    }

    public void requireCampaignExport() {
        require(campaignExport, "当前版本未开启求职周期档案功能。");
    }

    private void require(boolean enabled, String message) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
