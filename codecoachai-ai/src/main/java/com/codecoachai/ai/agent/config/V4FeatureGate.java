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

    @Value("${codecoachai.v4.features.adaptive-plan-enabled:false}")
    private boolean adaptivePlanEnabled;

    public void requireGrowthEnabled() {
        if (!growthEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前版本未开启 V4 成长与记忆功能。");
        }
    }

    public void requireKnowledgeEnabled() {
        if (!knowledgeEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前版本未开启 V4 个人知识库功能。");
        }
    }

    public void requireAdaptivePlanEnabled() {
        if (!adaptivePlanEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前版本未开启复盘驱动自适应计划功能。");
        }
    }

    public boolean isAdaptivePlanEnabled() {
        return adaptivePlanEnabled;
    }
}
