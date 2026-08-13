package com.codecoachai.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.vo.AiRuntimeStatusVO;
import com.codecoachai.ai.domain.vo.AiRuntimeStatusVO.ProviderStatus;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRuntimeStatusService {

    public static final String DEFAULT_MODEL_SCOPE = "GLOBAL";

    private final AiProperties aiProperties;
    private final AiRouterProperties routerProperties;
    private final AiModelConfigMapper modelConfigMapper;
    private final AesGcmTextEncryptor apiKeyEncryptor;

    public AiRuntimeStatusVO currentStatus() {
        AiRuntimeStatusVO result = baseStatus();
        List<AiModelConfig> databaseModels = loadDatabaseModels(result);
        Map<String, List<AiModelConfig>> modelsByProvider = groupByProvider(databaseModels);
        applyGlobalDefaultRouting(result, databaseModels);

        Set<String> providerNames = providerNames(modelsByProvider);
        for (String providerName : providerNames) {
            result.getProviders().add(providerStatus(providerName, modelsByProvider.getOrDefault(providerName, List.of())));
        }
        finishEffectiveMode(result);
        return result;
    }

    private AiRuntimeStatusVO baseStatus() {
        AiRouterProperties.Router router = routerProperties.getRouter();
        AiRuntimeStatusVO result = new AiRuntimeStatusVO();
        result.setServiceEnabled(Boolean.TRUE.equals(aiProperties.getEnabled()));
        result.setMockEnabled(Boolean.TRUE.equals(aiProperties.getMockEnabled()));
        result.setRealRoutingAllowed(Boolean.TRUE.equals(aiProperties.getEnabled())
                && !Boolean.TRUE.equals(aiProperties.getMockEnabled()));
        result.setDefaultModelScope(DEFAULT_MODEL_SCOPE);
        result.setDefaultModelScopeLabel("全局仅一个启用默认模型");
        result.setConfiguredDefaultProvider(router.getDefaultProvider());
        result.setFallbackEnabled(Boolean.TRUE.equals(router.getFallbackEnabled()));
        result.setConfiguredFallbackProvider(router.getFallbackProvider());
        result.setConfiguredEmbeddingProvider(router.getEmbeddingProvider());
        result.setLegacyProvider(aiProperties.getProvider());
        result.setLegacyClientConfigured(StringUtils.hasText(aiProperties.getBaseUrl())
                && StringUtils.hasText(aiProperties.getApiKey())
                && StringUtils.hasText(aiProperties.getModel()));
        result.setDatabaseStatus("AVAILABLE");
        return result;
    }

    private List<AiModelConfig> loadDatabaseModels(AiRuntimeStatusVO result) {
        try {
            List<AiModelConfig> models = modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                    .orderByAsc(AiModelConfig::getProvider)
                    .orderByDesc(AiModelConfig::getDefaultModel)
                    .orderByAsc(AiModelConfig::getSortOrder)
                    .orderByDesc(AiModelConfig::getUpdatedAt));
            return models == null ? List.of() : models;
        } catch (RuntimeException ex) {
            log.warn("Unable to load AI model governance state", ex);
            result.setDatabaseStatus("UNAVAILABLE");
            result.getRiskCodes().add("MODEL_DATABASE_UNAVAILABLE");
            result.getOperatorMessages().add("模型配置库暂时不可查询，当前状态仅基于运行配置计算");
            return List.of();
        }
    }

    private Map<String, List<AiModelConfig>> groupByProvider(List<AiModelConfig> models) {
        Map<String, List<AiModelConfig>> result = new LinkedHashMap<>();
        for (AiModelConfig model : models) {
            if (model != null && StringUtils.hasText(model.getProvider())) {
                result.computeIfAbsent(model.getProvider(), ignored -> new ArrayList<>()).add(model);
            }
        }
        return result;
    }

    private void applyGlobalDefaultRouting(AiRuntimeStatusVO result, List<AiModelConfig> models) {
        List<AiModelConfig> globalDefaults = models.stream()
                .filter(model -> Integer.valueOf(1).equals(model.getEnabled()))
                .filter(model -> Integer.valueOf(1).equals(model.getDefaultModel()))
                .toList();
        result.setGlobalDefaultModelCount(globalDefaults.size());
        if (globalDefaults.size() == 1) {
            AiModelConfig selected = globalDefaults.get(0);
            result.setEffectivePrimaryProvider(selected.getProvider());
            result.setEffectivePrimaryModel(selected.getModelCode());
            return;
        }
        result.setEffectivePrimaryProvider(routerProperties.getRouter().getDefaultProvider());
        if (globalDefaults.isEmpty()) {
            addRisk(result, "GLOBAL_DEFAULT_MISSING",
                    "未找到启用的全局默认模型，业务调用将回退到运行配置主供应商");
        } else {
            addRisk(result, "MULTIPLE_GLOBAL_DEFAULTS",
                    "存在多个启用默认模型，业务调用将回退到运行配置主供应商，需保留唯一全局默认模型");
        }
    }

    private Set<String> providerNames(Map<String, List<AiModelConfig>> modelsByProvider) {
        AiRouterProperties.Router router = routerProperties.getRouter();
        Set<String> names = new LinkedHashSet<>();
        addProvider(names, router.getDefaultProvider());
        if (Boolean.TRUE.equals(router.getFallbackEnabled())) {
            addProvider(names, router.getFallbackProvider());
        }
        addProvider(names, router.getEmbeddingProvider());
        routerProperties.getProviders().keySet().forEach(name -> addProvider(names, name));
        modelsByProvider.keySet().forEach(name -> addProvider(names, name));
        return names;
    }

    private ProviderStatus providerStatus(String providerName, List<AiModelConfig> models) {
        ProviderStatus status = new ProviderStatus();
        status.setProvider(providerName);
        status.setRouteRoles(routeRoles(providerName));

        List<AiModelConfig> enabledModels = models.stream()
                .filter(model -> Integer.valueOf(1).equals(model.getEnabled()))
                .toList();
        long defaultCount = models.stream()
                .filter(model -> Integer.valueOf(1).equals(model.getDefaultModel()))
                .count();
        status.setActiveModelCount(enabledModels.size());
        status.setDefaultModelCount(Math.toIntExact(defaultCount));
        status.setProviderDefaultContractSatisfied(defaultCount <= 1);
        if (defaultCount > 1) {
            status.getRiskCodes().add("MULTIPLE_PROVIDER_DEFAULTS_LEGACY");
        }

        AiModelConfig databaseCandidate = enabledModels.isEmpty() ? null : enabledModels.get(0);
        AiRouterProperties.ProviderConfig configCandidate = routerProperties.getProviders().get(providerName);
        applyEffectiveProviderConfig(status, databaseCandidate, configCandidate);
        return status;
    }

    private void applyEffectiveProviderConfig(
            ProviderStatus status,
            AiModelConfig databaseCandidate,
            AiRouterProperties.ProviderConfig configCandidate) {
        if (databaseCandidate != null) {
            CredentialCheck credential = credential(databaseCandidate.getApiKey());
            boolean endpointReady = StringUtils.hasText(databaseCandidate.getApiBaseUrl());
            boolean modelReady = StringUtils.hasText(databaseCandidate.getModelCode());
            boolean databaseReady = endpointReady && credential.usable() && modelReady;

            status.setSelectedModelCode(databaseCandidate.getModelCode());
            status.setEndpointConfigured(endpointReady);
            status.setCredentialConfigured(credential.configured());
            status.setCredentialEncrypted(credential.encrypted());
            status.setCredentialUsable(credential.usable());
            status.setModelConfigured(modelReady);
            status.setGovernanceStatus(StringUtils.hasText(databaseCandidate.getGovernanceStatus())
                    ? databaseCandidate.getGovernanceStatus() : "ACTIVE");
            if ("PLACEHOLDER".equalsIgnoreCase(status.getGovernanceStatus())) {
                status.getRiskCodes().add("PLACEHOLDER_MODEL_SELECTED");
            }
            if (credential.decryptFailed()) {
                status.setEffectiveConfigSource("DATABASE_BLOCKED");
                status.setReadyForCall(false);
                status.getRiskCodes().add("DATABASE_CREDENTIAL_DECRYPT_FAILED");
                return;
            }
            if (credential.configured() && !credential.encrypted()) {
                status.getRiskCodes().add("DATABASE_CREDENTIAL_NOT_ENCRYPTED");
            }
            if (databaseReady) {
                status.setEffectiveConfigSource("DATABASE");
                status.setReadyForCall(true);
                return;
            }
            status.getRiskCodes().add("DATABASE_MODEL_INCOMPLETE_USING_CONFIG_FALLBACK");
        }

        boolean configEndpointReady = configCandidate != null && StringUtils.hasText(configCandidate.getBaseUrl());
        boolean configCredentialReady = configCandidate != null && StringUtils.hasText(configCandidate.getApiKey());
        boolean needsChatModel = status.getRouteRoles().stream()
                .anyMatch(role -> "PRIMARY".equals(role) || "FALLBACK".equals(role));
        boolean needsEmbeddingModel = status.getRouteRoles().contains("EMBEDDING");
        boolean chatModelReady = configCandidate != null && StringUtils.hasText(configCandidate.getChatModel());
        boolean embeddingModelReady = configCandidate != null && StringUtils.hasText(configCandidate.getEmbeddingModel());
        boolean configModelReady = (!needsChatModel || chatModelReady)
                && (!needsEmbeddingModel || embeddingModelReady);
        status.setEndpointConfigured(configEndpointReady);
        status.setCredentialConfigured(configCredentialReady);
        status.setCredentialEncrypted(null);
        status.setCredentialUsable(configCredentialReady);
        status.setModelConfigured(configModelReady);
        status.setSelectedModelCode(configCandidate == null ? status.getSelectedModelCode()
                : selectedModelCode(configCandidate, needsChatModel, needsEmbeddingModel));
        status.setEffectiveConfigSource(configEndpointReady && configCredentialReady ? "CONFIG" : "NONE");
        status.setReadyForCall(configEndpointReady && configCredentialReady && configModelReady);
        if (!Boolean.TRUE.equals(status.getReadyForCall())) {
            status.getRiskCodes().add("PROVIDER_CONFIG_INCOMPLETE");
        }
    }

    private String selectedModelCode(
            AiRouterProperties.ProviderConfig config,
            boolean needsChatModel,
            boolean needsEmbeddingModel) {
        if (needsChatModel && StringUtils.hasText(config.getChatModel())) {
            return config.getChatModel();
        }
        if (needsEmbeddingModel && StringUtils.hasText(config.getEmbeddingModel())) {
            return config.getEmbeddingModel();
        }
        return firstText(config.getChatModel(), config.getReasonerModel(), config.getEmbeddingModel());
    }

    private CredentialCheck credential(String storedCredential) {
        if (!StringUtils.hasText(storedCredential)) {
            return new CredentialCheck(false, false, false, false);
        }
        boolean encrypted = apiKeyEncryptor.isEncrypted(storedCredential);
        if (!encrypted) {
            return new CredentialCheck(true, false, true, false);
        }
        try {
            return new CredentialCheck(
                    true,
                    true,
                    StringUtils.hasText(apiKeyEncryptor.decryptIfNeeded(storedCredential)),
                    false);
        } catch (IllegalStateException ex) {
            return new CredentialCheck(true, true, false, true);
        }
    }

    private List<String> routeRoles(String providerName) {
        AiRouterProperties.Router router = routerProperties.getRouter();
        List<String> roles = new ArrayList<>();
        if (providerName.equals(router.getDefaultProvider())) {
            roles.add("PRIMARY");
        }
        if (Boolean.TRUE.equals(router.getFallbackEnabled())
                && providerName.equals(router.getFallbackProvider())) {
            roles.add("FALLBACK");
        }
        if (providerName.equals(router.getEmbeddingProvider())) {
            roles.add("EMBEDDING");
        }
        return roles;
    }

    private void finishEffectiveMode(AiRuntimeStatusVO result) {
        result.getProviders().forEach(provider -> {
            if (!Boolean.TRUE.equals(provider.getProviderDefaultContractSatisfied())) {
                addRisk(result, "MULTIPLE_PROVIDER_DEFAULTS_LEGACY",
                        "同一供应商存在多个历史默认标记，请保留唯一全局默认模型");
            }
            provider.getRiskCodes().forEach(code -> {
                if (!result.getRiskCodes().contains(code)) {
                    result.getRiskCodes().add(code);
                }
                String message = providerRiskMessage(provider.getProvider(), code);
                if (message != null && !result.getOperatorMessages().contains(message)) {
                    result.getOperatorMessages().add(message);
                }
            });
        });

        if (!Boolean.TRUE.equals(result.getServiceEnabled())) {
            result.setEffectiveMode("DISABLED");
            result.setEffectiveModeLabel("AI 服务已禁用");
            result.getOperatorMessages().add("真实模型路由已被服务总开关阻止");
            return;
        }
        if (Boolean.TRUE.equals(result.getMockEnabled())) {
            result.setEffectiveMode("MOCK");
            result.setEffectiveModeLabel("模拟数据模式");
            result.getOperatorMessages().add("真实模型路由已被 Mock 开关阻止，不会调用供应商");
            return;
        }
        ProviderStatus primary = result.getProviders().stream()
                .filter(provider -> provider.getProvider().equals(result.getEffectivePrimaryProvider()))
                .findFirst()
                .orElse(null);
        if (primary != null && Boolean.TRUE.equals(primary.getReadyForCall())) {
            result.setEffectiveMode("REAL");
            result.setEffectiveModeLabel("真实模型路由");
            result.getOperatorMessages().add("主路由将使用 "
                    + result.getEffectivePrimaryProvider() + " / "
                    + (StringUtils.hasText(result.getEffectivePrimaryModel())
                    ? result.getEffectivePrimaryModel() : primary.getSelectedModelCode())
                    + "（" + primary.getEffectiveConfigSource() + "）");
            return;
        }
        result.setEffectiveMode("DEGRADED");
        result.setEffectiveModeLabel("真实路由配置不完整");
        addRisk(result, "PRIMARY_PROVIDER_NOT_READY", "主供应商当前不具备完整调用条件");
    }

    private void addRisk(AiRuntimeStatusVO result, String code, String message) {
        if (!result.getRiskCodes().contains(code)) {
            result.getRiskCodes().add(code);
        }
        if (!result.getOperatorMessages().contains(message)) {
            result.getOperatorMessages().add(message);
        }
    }

    private String providerRiskMessage(String provider, String riskCode) {
        return switch (riskCode) {
            case "MULTIPLE_PROVIDER_DEFAULTS_LEGACY" ->
                    "供应商 " + provider + " 存在多个历史默认标记，请保留唯一全局默认模型";
            case "PLACEHOLDER_MODEL_SELECTED" ->
                    "供应商 " + provider + " 当前选中了历史占位模型，请核对后再决定是否处置";
            case "DATABASE_CREDENTIAL_DECRYPT_FAILED" ->
                    "供应商 " + provider + " 的数据库密钥无法解密，真实调用会被阻止";
            case "DATABASE_CREDENTIAL_NOT_ENCRYPTED" ->
                    "供应商 " + provider + " 的数据库密钥仍是明文格式，应通过管理端重新保存完成加密";
            case "DATABASE_MODEL_INCOMPLETE_USING_CONFIG_FALLBACK" ->
                    "供应商 " + provider + " 的数据库模型配置不完整，当前改用运行配置";
            case "PROVIDER_CONFIG_INCOMPLETE" ->
                    "供应商 " + provider + " 缺少可用的地址、凭据或模型标识";
            default -> null;
        };
    }

    private void addProvider(Set<String> providers, String provider) {
        if (StringUtils.hasText(provider)) {
            providers.add(provider);
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record CredentialCheck(
            boolean configured, boolean encrypted, boolean usable, boolean decryptFailed) {
    }
}
