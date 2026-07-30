package com.codecoachai.resume.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
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

    public void requireCampaignExport() {
        require(campaignExport, "当前版本未开启求职周期档案导出功能。");
    }

    public List<String> enabledCapabilities() {
        List<String> capabilities = new ArrayList<>();
        if (campaignCockpit) {
            capabilities.add("CAMPAIGN_COCKPIT");
        }
        if (campaignPulse) {
            capabilities.add("CAMPAIGN_PULSE");
        }
        if (campaignPlan) {
            capabilities.add("CAMPAIGN_PLAN");
        }
        if (campaignPortfolio) {
            capabilities.add("CAMPAIGN_PORTFOLIO");
        }
        if (campaignExport) {
            capabilities.add("CAMPAIGN_EXPORT");
        }
        return capabilities;
    }

    private void require(boolean enabled, String message) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
