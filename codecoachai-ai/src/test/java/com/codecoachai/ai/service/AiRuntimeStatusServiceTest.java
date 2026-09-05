package com.codecoachai.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.vo.AiRuntimeStatusVO;
import com.codecoachai.ai.domain.vo.AiRuntimeStatusVO.ProviderStatus;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AiRuntimeStatusServiceTest {

    private AiProperties aiProperties;
    private AiRouterProperties routerProperties;
    private AiModelConfigMapper mapper;
    private AesGcmTextEncryptor encryptor;
    private StandardEnvironment environment;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setMockEnabled(false);
        routerProperties = new AiRouterProperties();
        routerProperties.getRouter().setDefaultProvider("deepseek");
        routerProperties.getRouter().setFallbackProvider("dashscope");
        routerProperties.getRouter().setFallbackEnabled(true);
        routerProperties.getRouter().setEmbeddingProvider("dashscope");
        mapper = mock(AiModelConfigMapper.class);
        encryptor = mock(AesGcmTextEncryptor.class);
        environment = new StandardEnvironment();
    }

    @Test
    void mockModeBlocksRealRoutingAndUsesSingleGlobalDefault() {
        aiProperties.setMockEnabled(true);
        when(mapper.selectList(any())).thenReturn(List.of(
                model(1L, "deepseek", "deepseek-chat", 1, 1, "PLACEHOLDER"),
                model(2L, "dashscope", "qwen-plus", 1, 1, "ACTIVE")));

        AiRuntimeStatusVO status = service().currentStatus();

        assertEquals("MOCK", status.getEffectiveMode());
        assertFalse(status.getRealRoutingAllowed());
        assertEquals("GLOBAL", status.getDefaultModelScope());
        assertEquals(2, status.getGlobalDefaultModelCount());
        assertTrue(status.getProviders().stream()
                .allMatch(ProviderStatus::getProviderDefaultContractSatisfied));
        assertTrue(status.getRiskCodes().contains("PLACEHOLDER_MODEL_SELECTED"));
        assertTrue(status.getOperatorMessages().stream()
                .anyMatch(message -> message.contains("不会调用供应商")));
    }

    @Test
    void nacosIsReportedAsTheAuthoritativeMockConfigurationSource() {
        environment.getPropertySources().addFirst(new MapPropertySource(
                "nacos-codecoachai-ai-dev.yml",
                Map.of(AiProperties.MOCK_ENABLED_PROPERTY, "false")));
        when(mapper.selectList(any())).thenReturn(List.of(
                model(1L, "deepseek", "deepseek-chat", 1, 1, "ACTIVE")));

        AiRuntimeStatusVO status = service().currentStatus();

        assertTrue(status.getMockConfigurationConfigured());
        assertEquals("NACOS", status.getMockConfigurationSource());
        assertFalse(status.getMockEnabled());
        assertTrue(status.getRealRoutingAllowed());
        assertTrue(status.getOperatorMessages().stream()
                .anyMatch(message -> message.contains("system_config.ai.mock.enabled")));
    }

    @Test
    void missingMockConfigurationFailsClosedInsteadOfTreatingDefaultFalseAsConfigured() {
        aiProperties.setMockEnabled(null);
        when(mapper.selectList(any())).thenReturn(List.of(
                model(1L, "deepseek", "deepseek-chat", 1, 1, "ACTIVE")));

        AiRuntimeStatusVO status = service().currentStatus();

        assertFalse(status.getMockConfigurationConfigured());
        assertEquals("UNCONFIGURED", status.getMockConfigurationSource());
        assertEquals(null, status.getMockEnabled());
        assertFalse(status.getRealRoutingAllowed());
        assertEquals("DEGRADED", status.getEffectiveMode());
        assertTrue(status.getRiskCodes().contains("MOCK_MODE_NOT_EXPLICITLY_CONFIGURED"));
    }

    @Test
    void multipleDefaultsWithinSameProviderAreReportedAsContractViolation() {
        when(mapper.selectList(any())).thenReturn(List.of(
                model(1L, "deepseek", "deepseek-chat", 1, 1, "ACTIVE"),
                model(2L, "deepseek", "deepseek-reasoner", 1, 1, "ACTIVE")));

        AiRuntimeStatusVO status = service().currentStatus();
        ProviderStatus deepseek = provider(status, "deepseek");

        assertEquals(2, deepseek.getDefaultModelCount());
        assertFalse(deepseek.getProviderDefaultContractSatisfied());
        assertTrue(status.getRiskCodes().contains("MULTIPLE_GLOBAL_DEFAULTS"));
    }

    @Test
    void encryptedDatabaseCredentialFailureIsVisibleWithoutExposingCredential() {
        AiModelConfig model = model(1L, "deepseek", "deepseek-chat", 1, 1, "ACTIVE");
        model.setApiKey("{aes-gcm-v1}ciphertext");
        when(mapper.selectList(any())).thenReturn(List.of(model));
        when(encryptor.isEncrypted("{aes-gcm-v1}ciphertext")).thenReturn(true);
        when(encryptor.decryptIfNeeded("{aes-gcm-v1}ciphertext"))
                .thenThrow(new IllegalStateException("wrong key"));

        AiRuntimeStatusVO status = service().currentStatus();
        ProviderStatus deepseek = provider(status, "deepseek");

        assertEquals("DATABASE_BLOCKED", deepseek.getEffectiveConfigSource());
        assertFalse(deepseek.getCredentialUsable());
        assertTrue(deepseek.getRiskCodes().contains("DATABASE_CREDENTIAL_DECRYPT_FAILED"));
        assertFalse(status.toString().contains("ciphertext"));
        assertFalse(status.toString().contains("wrong key"));
    }

    @Test
    void completeRuntimeConfigRemainsVisibleButCannotReplaceMissingDatabaseDefault() {
        AiRouterProperties.ProviderConfig config = new AiRouterProperties.ProviderConfig();
        config.setBaseUrl("https://api.example.com/v1");
        config.setApiKey("secret");
        config.setChatModel("deepseek-chat");
        routerProperties.getProviders().put("deepseek", config);
        when(mapper.selectList(any())).thenReturn(List.of());

        AiRuntimeStatusVO status = service().currentStatus();
        ProviderStatus deepseek = provider(status, "deepseek");

        assertEquals("DEGRADED", status.getEffectiveMode());
        assertFalse(status.getRealRoutingAllowed());
        assertEquals(null, status.getEffectivePrimaryProvider());
        assertEquals("CONFIG", deepseek.getEffectiveConfigSource());
        assertTrue(deepseek.getReadyForCall());
        assertFalse(status.toString().contains("secret"));
    }

    @Test
    void primaryProviderWithOnlyEmbeddingModelIsNotReadyForChatRouting() {
        routerProperties.getRouter().setFallbackEnabled(false);
        routerProperties.getRouter().setEmbeddingProvider("dashscope");
        AiRouterProperties.ProviderConfig config = new AiRouterProperties.ProviderConfig();
        config.setBaseUrl("https://api.example.com/v1");
        config.setApiKey("secret");
        config.setEmbeddingModel("text-embedding-3-small");
        routerProperties.getProviders().put("deepseek", config);
        when(mapper.selectList(any())).thenReturn(List.of());

        AiRuntimeStatusVO status = service().currentStatus();
        ProviderStatus deepseek = provider(status, "deepseek");

        assertFalse(deepseek.getModelConfigured());
        assertFalse(deepseek.getReadyForCall());
        assertEquals("DEGRADED", status.getEffectiveMode());
    }

    @Test
    void embeddingProviderWithOnlyChatModelIsNotReadyForEmbeddingRouting() {
        routerProperties.getRouter().setFallbackEnabled(false);
        routerProperties.getRouter().setDefaultProvider("deepseek");
        routerProperties.getRouter().setEmbeddingProvider("dashscope");
        AiRouterProperties.ProviderConfig config = new AiRouterProperties.ProviderConfig();
        config.setBaseUrl("https://api.example.com/v1");
        config.setApiKey("secret");
        config.setChatModel("qwen-plus");
        routerProperties.getProviders().put("dashscope", config);
        when(mapper.selectList(any())).thenReturn(List.of());

        AiRuntimeStatusVO status = service().currentStatus();
        ProviderStatus dashscope = provider(status, "dashscope");

        assertTrue(dashscope.getRouteRoles().contains("EMBEDDING"));
        assertFalse(dashscope.getModelConfigured());
        assertFalse(dashscope.getReadyForCall());
    }

    @Test
    void missingAndDisabledProviderDefaultsAreDiagnosedWithoutChangingSelection() {
        AiModelConfig disabledDefault = model(
                1L, "deepseek", "deepseek-old", 1, 0, "ACTIVE");
        AiModelConfig enabledNonDefault = model(
                2L, "deepseek", "deepseek-chat", 0, 1, "ACTIVE");
        when(mapper.selectList(any())).thenReturn(List.of(disabledDefault, enabledNonDefault));

        AiRuntimeStatusVO status = service().currentStatus();
        ProviderStatus deepseek = provider(status, "deepseek");

        assertEquals("deepseek-chat", deepseek.getSelectedModelCode());
        assertEquals(null, status.getEffectivePrimaryProvider());
        assertFalse(status.getRealRoutingAllowed());
        assertTrue(status.getRiskCodes().contains("GLOBAL_DEFAULT_MISSING"));
        assertTrue(status.getOperatorMessages().stream()
                .anyMatch(message -> message.contains("真实业务调用已阻止")));
    }

    @Test
    void uniqueEnabledGlobalDefaultBecomesActualPrimaryRoute() {
        AiModelConfig globalDefault = model(
                1L, "WEIXIN_OPENAI_COMPATIBLE", "Deepseek-v4-flash", 1, 1, "ACTIVE");
        AiModelConfig inactiveLegacyModel = model(
                2L, "deepseek", "deepseek-chat", 0, 0, "PLACEHOLDER");
        when(mapper.selectList(any())).thenReturn(List.of(globalDefault, inactiveLegacyModel));

        AiRuntimeStatusVO status = service().currentStatus();

        assertEquals("WEIXIN_OPENAI_COMPATIBLE", status.getEffectivePrimaryProvider());
        assertEquals("Deepseek-v4-flash", status.getEffectivePrimaryModel());
        assertEquals(1, status.getGlobalDefaultModelCount());
        assertTrue(status.getOperatorMessages().stream()
                .anyMatch(message -> message.contains("Deepseek-v4-flash")));
    }

    private AiRuntimeStatusService service() {
        return new AiRuntimeStatusService(aiProperties, routerProperties, mapper, encryptor, environment);
    }

    private ProviderStatus provider(AiRuntimeStatusVO status, String provider) {
        return status.getProviders().stream()
                .filter(item -> provider.equals(item.getProvider()))
                .findFirst()
                .orElseThrow();
    }

    private AiModelConfig model(
            Long id,
            String provider,
            String modelCode,
            Integer defaultModel,
            Integer enabled,
            String governanceStatus) {
        AiModelConfig model = new AiModelConfig();
        model.setId(id);
        model.setProvider(provider);
        model.setModelCode(modelCode);
        model.setDefaultModel(defaultModel);
        model.setEnabled(enabled);
        model.setApiBaseUrl("https://api.example.com/v1");
        model.setApiKey("plain-test-key");
        model.setGovernanceStatus(governanceStatus);
        return model;
    }
}
