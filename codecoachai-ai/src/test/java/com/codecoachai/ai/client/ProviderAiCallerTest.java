package com.codecoachai.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import com.codecoachai.ai.security.AiProviderEndpointPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderAiCallerTest {

    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private AesGcmTextEncryptor apiKeyEncryptor;
    @Mock
    private AiProviderEndpointPolicy endpointPolicy;
    @Mock
    private SecureProviderHttpClient providerHttpClient;

    private AiRouterProperties routerProperties;
    private ProviderAiCaller caller;

    @BeforeEach
    void setUp() {
        routerProperties = new AiRouterProperties();
        AiRouterProperties.ProviderConfig staticConfig = new AiRouterProperties.ProviderConfig();
        staticConfig.setBaseUrl("https://static.example.com/v1");
        staticConfig.setApiKey("static-key");
        staticConfig.setChatModel("static-model");
        routerProperties.getProviders().put("deepseek", staticConfig);
        caller = new ProviderAiCaller(
                routerProperties,
                modelConfigMapper,
                apiKeyEncryptor,
                new ObjectMapper(),
                endpointPolicy,
                providerHttpClient);
    }

    @Test
    void enabledDatabaseModelOverridesStaticProviderConfig() throws Exception {
        AiModelConfig databaseModel = model(7L, "https://db.example.com/v1", "db-model", "encrypted-db-key");
        when(modelConfigMapper.selectOne(any())).thenReturn(databaseModel);
        when(apiKeyEncryptor.decryptIfNeeded("encrypted-db-key")).thenReturn("db-key");
        when(endpointPolicy.chatEndpoint("https://db.example.com/v1")).thenReturn(URI.create(
                "https://db.example.com/v1/chat/completions"));
        when(providerHttpClient.postJson(
                eq(URI.create("https://db.example.com/v1/chat/completions")),
                eq("db-key"),
                any(String.class),
                any(Duration.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        ProviderAiCaller.CallResult result = caller.chat("deepseek", "hello", "chat");

        assertEquals("db-model", result.getModel());
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerHttpClient).postJson(
                eq(URI.create("https://db.example.com/v1/chat/completions")),
                eq("db-key"),
                bodyCaptor.capture(),
                any(Duration.class));
        assertEquals("db-model", new ObjectMapper().readTree(bodyCaptor.getValue()).path("model").asText());
    }

    @Test
    void staticProviderConfigRemainsFallbackWhenDatabaseHasNoEnabledModel() throws Exception {
        when(modelConfigMapper.selectOne(any())).thenReturn(null);
        when(endpointPolicy.chatEndpoint("https://static.example.com/v1")).thenReturn(URI.create(
                "https://static.example.com/v1/chat/completions"));
        when(providerHttpClient.postJson(
                eq(URI.create("https://static.example.com/v1/chat/completions")),
                eq("static-key"),
                any(String.class),
                any(Duration.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        ProviderAiCaller.CallResult result = caller.chat("deepseek", "hello", "chat");

        assertEquals("static-model", result.getModel());
        verify(providerHttpClient).postJson(
                eq(URI.create("https://static.example.com/v1/chat/completions")),
                eq("static-key"),
                any(String.class),
                any(Duration.class));
    }

    private static AiModelConfig model(Long id, String baseUrl, String modelCode, String apiKey) {
        AiModelConfig model = new AiModelConfig();
        model.setId(id);
        model.setProvider("deepseek");
        model.setApiBaseUrl(baseUrl);
        model.setModelCode(modelCode);
        model.setApiKey(apiKey);
        model.setEnabled(1);
        model.setDefaultModel(1);
        return model;
    }
}
