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
@ConfigurationProperties(prefix = "codecoachai.features.v9")
public class V9FeatureGate {

    private boolean evidenceUsage;
    private boolean evidenceFeedback;
    private boolean evidenceAssetsView;

    public void requireEvidenceUsage() {
        require(evidenceUsage, "当前版本未开启证据使用记录功能。");
    }

    public void requireEvidenceFeedback() {
        require(evidenceFeedback, "当前版本未开启证据结果反馈功能。");
    }

    public void requireEvidenceAssetsView() {
        require(evidenceAssetsView, "当前版本未开启证据资产视图功能。");
    }

    public List<String> enabledCapabilities() {
        List<String> capabilities = new ArrayList<>();
        if (evidenceUsage) {
            capabilities.add("EVIDENCE_USAGE");
        }
        if (evidenceFeedback) {
            capabilities.add("EVIDENCE_FEEDBACK");
        }
        if (evidenceAssetsView) {
            capabilities.add("EVIDENCE_ASSETS_VIEW");
        }
        return capabilities;
    }

    private void require(boolean enabled, String message) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
