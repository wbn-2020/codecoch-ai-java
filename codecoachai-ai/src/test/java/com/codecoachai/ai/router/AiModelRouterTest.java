package com.codecoachai.ai.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.client.ProviderAiCaller;
import com.codecoachai.ai.client.ProviderAiCaller.CallResult;
import com.codecoachai.ai.config.AiRouterProperties;
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

    @BeforeEach
    void setUp() {
        AiRouterProperties properties = new AiRouterProperties();
        properties.getRouter().setDefaultProvider("deepseek");
        properties.getRouter().setFallbackProvider("dashscope");
        properties.getRouter().setFallbackEnabled(true);
        properties.getRetry().setMaxAttempts(1);
        router = new AiModelRouter(properties, providerAiCaller, new RetryGuard(properties), tokenAccountant);
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
}
