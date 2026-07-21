package com.codecoachai.resume.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * V7 后端最终门禁。前端开关只负责隐藏入口，真正的能力保护必须在服务端完成。
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "codecoachai.features.v7")
public class V7FeatureGate {

    private boolean campaignWorkspace;

    private boolean realInterview;

    private boolean offer;

    private boolean contactActivity;

    private boolean research;

    public void setCampaignWorkspace(boolean campaignWorkspace) {
        this.campaignWorkspace = campaignWorkspace;
    }

    public void setRealInterview(boolean realInterview) {
        this.realInterview = realInterview;
    }

    public void setOffer(boolean offer) {
        this.offer = offer;
    }

    public void setContactActivity(boolean contactActivity) {
        this.contactActivity = contactActivity;
    }

    public void setResearch(boolean research) {
        this.research = research;
    }

    public void requireCampaignWorkspace() {
        require(campaignWorkspace, "当前版本未开启求职周期与机会工作区功能。");
    }

    public void requireRealInterview() {
        require(realInterview, "当前版本未开启真实面试功能。");
    }

    public void requireOffer() {
        require(offer, "当前版本未开启 Offer 决策功能。");
    }

    public void requireContactActivity() {
        require(contactActivity, "当前版本未开启联系人与活动功能。");
    }

    public void requireResearch() {
        require(research, "当前版本未开启机会研究功能。");
    }

    public List<String> enabledCapabilities() {
        List<String> capabilities = new ArrayList<>();
        if (realInterview) {
            capabilities.add("REAL_INTERVIEW");
        }
        if (offer) {
            capabilities.add("OFFER");
        }
        if (contactActivity) {
            capabilities.add("CONTACT_ACTIVITY");
        }
        if (research) {
            capabilities.add("RESEARCH");
        }
        return capabilities;
    }

    private void require(boolean enabled, String message) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
