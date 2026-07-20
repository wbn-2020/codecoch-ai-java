package com.codecoachai.resume.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * V7 后端最终门禁。前端开关只负责隐藏入口，真正的能力保护必须在服务端完成。
 */
@Component
public class V7FeatureGate {

    @Value("${codecoachai.features.v7.campaign-workspace:false}")
    private boolean campaignWorkspace;

    @Value("${codecoachai.features.v7.real-interview:false}")
    private boolean realInterview;

    @Value("${codecoachai.features.v7.offer:false}")
    private boolean offer;

    @Value("${codecoachai.features.v7.contact-activity:false}")
    private boolean contactActivity;

    @Value("${codecoachai.features.v7.research:false}")
    private boolean research;

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
