package com.codecoachai.ai.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AiRuntimeStatusVO {

    private Boolean serviceEnabled;
    private Boolean mockEnabled;
    private Boolean realRoutingAllowed;
    private String effectiveMode;
    private String effectiveModeLabel;
    private String defaultModelScope;
    private String defaultModelScopeLabel;
    private String configuredDefaultProvider;
    private String effectivePrimaryProvider;
    private String effectivePrimaryModel;
    private Integer globalDefaultModelCount;
    private Boolean fallbackEnabled;
    private String configuredFallbackProvider;
    private String configuredEmbeddingProvider;
    private String databaseStatus;
    private Boolean legacyClientConfigured;
    private String legacyProvider;
    private List<ProviderStatus> providers = new ArrayList<>();
    private List<String> riskCodes = new ArrayList<>();
    private List<String> operatorMessages = new ArrayList<>();

    @Data
    public static class ProviderStatus {

        private String provider;
        private List<String> routeRoles = new ArrayList<>();
        private String effectiveConfigSource;
        private String selectedModelCode;
        private Integer activeModelCount;
        private Integer defaultModelCount;
        private Boolean providerDefaultContractSatisfied;
        private Boolean endpointConfigured;
        private Boolean credentialConfigured;
        private Boolean credentialEncrypted;
        private Boolean credentialUsable;
        private Boolean modelConfigured;
        private Boolean readyForCall;
        private String governanceStatus;
        private List<String> riskCodes = new ArrayList<>();
    }
}
