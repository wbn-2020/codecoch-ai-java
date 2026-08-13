package com.codecoachai.ai.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.client.ProviderAiCaller;
import com.codecoachai.ai.client.ProviderAiCaller.CallResult;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.guard.RetryGuard;
import com.codecoachai.ai.guard.TokenAccountant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiModelRouterTest {

    @Mock
    private ProviderAiCaller providerAiCaller;
    @Mock
    private TokenAccountant tokenAccountant;

    private AiModelRouter router;
    private AiProperties aiProperties;

    @BeforeEach
    void setUp() {
        AiRouterProperties properties = new AiRouterProperties();
        properties.getRouter().setDefaultProvider("deepseek");
        properties.getRouter().setFallbackProvider("dashscope");
        properties.getRouter().setFallbackEnabled(true);
        properties.getRetry().setMaxAttempts(1);
        aiProperties = new AiProperties();
        router = new AiModelRouter(
                properties, aiProperties, providerAiCaller, new RetryGuard(properties), tokenAccountant);
    }

    @Test
    void primaryProviderSuccessMarksSourceAsLlm() {
        doAnswer(invocation -> callResult(invocation.getArgument(0), "deepseek-chat"))
                .when(providerAiCaller).chat(eq("deepseek"), anyString(), eq("chat"));

        AiModelRouter.RouteResult result = router.chat(context());

        assertEquals("LLM", result.getResultSource());
        assertEquals("deepseek", result.getRouteTrace());
    }

    @Test
    void fallbackProviderSuccessMarksSourceAsFallback() {
        doThrow(new AiProviderException(AiFailureType.TIMEOUT, "primary timeout"))
                .when(providerAiCaller).chat(eq("deepseek"), anyString(), eq("chat"));
        doAnswer(invocation -> callResult(invocation.getArgument(0), "qwen-plus"))
                .when(providerAiCaller).chat(eq("dashscope"), anyString(), eq("chat"));

        AiModelRouter.RouteResult result = router.chat(context());

        assertEquals("FALLBACK", result.getResultSource());
        assertEquals("deepseek -> dashscope", result.getRouteTrace());
        verify(tokenAccountant).accumulate(10L, 3, 5, 0.01D);
    }

    @Test
    void uniqueDatabaseDefaultOverridesRuntimePrimaryForRegularCalls() {
        AiModelConfig databaseDefault = databaseDefault();
        when(providerAiCaller.findUniqueEnabledGlobalDefaultModel()).thenReturn(databaseDefault);
        when(providerAiCaller.chat(eq(databaseDefault), anyString(), eq("chat")))
                .thenReturn(callResult("WEIXIN_OPENAI_COMPATIBLE", "Deepseek-v4-flash"));

        AiModelRouter.RouteResult result = router.chat(context());

        assertEquals("WEIXIN_OPENAI_COMPATIBLE", result.getProvider());
        assertEquals("Deepseek-v4-flash", result.getModel());
        assertEquals("database-default:WEIXIN_OPENAI_COMPATIBLE/Deepseek-v4-flash", result.getRouteTrace());
        verify(providerAiCaller).chat(eq(databaseDefault), anyString(), eq("chat"));
    }

    @Test
    void uniqueDatabaseDefaultOverridesRuntimePrimaryForStreamingCalls() {
        AiModelConfig databaseDefault = databaseDefault();
        when(providerAiCaller.findUniqueEnabledGlobalDefaultModel()).thenReturn(databaseDefault);
        when(providerAiCaller.chatStream(eq(databaseDefault), anyString(), eq("chat"), any()))
                .thenReturn(callResult("WEIXIN_OPENAI_COMPATIBLE", "Deepseek-v4-flash"));

        AiModelRouter.RouteResult result = router.chatStream(context(), ignored -> { });

        assertEquals("WEIXIN_OPENAI_COMPATIBLE", result.getProvider());
        assertEquals("Deepseek-v4-flash", result.getModel());
        assertEquals("database-default:WEIXIN_OPENAI_COMPATIBLE/Deepseek-v4-flash", result.getRouteTrace());
    }

    @Test
    void forcedProviderBypassesDatabaseGlobalDefault() {
        doAnswer(invocation -> callResult(invocation.getArgument(0), "qwen-plus"))
                .when(providerAiCaller).chat(eq("dashscope"), anyString(), eq("chat"));
        AiModelRouter.AiCallContext ctx = context();
        ctx.setForceProvider("dashscope");

        AiModelRouter.RouteResult result = router.chat(ctx);

        assertEquals("dashscope", result.getProvider());
        assertEquals("dashscope", result.getRouteTrace());
    }

    @Test
    void mockModeCannotAccidentallyCallRealProvider() {
        aiProperties.setMockEnabled(true);

        AiProviderException exception = org.junit.jupiter.api.Assertions.assertThrows(
                AiProviderException.class, () -> router.chat(context()));

        assertEquals(AiFailureType.CONFIG_ERROR, exception.getFailureType());
    }

    @Test
    void disabledServiceCannotAccidentallyCallRealProvider() {
        aiProperties.setEnabled(false);

        AiProviderException exception = org.junit.jupiter.api.Assertions.assertThrows(
                AiProviderException.class, () -> router.chat(context()));

        assertEquals(AiFailureType.CONFIG_ERROR, exception.getFailureType());
    }

    private AiModelRouter.AiCallContext context() {
        AiModelRouter.AiCallContext ctx = new AiModelRouter.AiCallContext();
        ctx.setScene("PHASE2_TEST");
        ctx.setPrompt("请分析这份 Java 简历");
        ctx.setUserId(10L);
        ctx.setCheckQuota(false);
        return ctx;
    }

    private CallResult callResult(String provider, String model) {
        CallResult result = new CallResult();
        result.setProvider(provider);
        result.setModel(model);
        result.setContent("ok");
        result.setPromptTokens(3);
        result.setCompletionTokens(5);
        result.setTotalTokens(8);
        result.setEstimatedCost(0.01D);
        result.setElapsedMs(100L);
        return result;
    }

    private AiModelConfig databaseDefault() {
        AiModelConfig model = new AiModelConfig();
        model.setId(99L);
        model.setProvider("WEIXIN_OPENAI_COMPATIBLE");
        model.setModelCode("Deepseek-v4-flash");
        model.setEnabled(1);
        model.setDefaultModel(1);
        return model;
    }
}
