package com.codecoachai.ai.agent.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "codecoachai.features.v9")
public class V9FeatureGate {

    private boolean evidenceLearning;

    public void requireEvidenceLearning() {
        if (!evidenceLearning) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前版本未开启 V9 证据学习功能。");
        }
    }
}
