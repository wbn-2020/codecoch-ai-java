package com.codecoachai.ai.agent.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class V4FeatureGate {

    @Value("${codecoachai.v4.features.growth-enabled:false}")
    private boolean growthEnabled;

    @Value("${codecoachai.v4.features.knowledge-enabled:false}")
    private boolean knowledgeEnabled;

    public void requireGrowthEnabled() {
        if (!growthEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "V4 growth and memory features are not enabled in the current release.");
        }
    }

    public void requireKnowledgeEnabled() {
        if (!knowledgeEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "V4 personal knowledge features are not enabled in the current release.");
        }
    }
}
