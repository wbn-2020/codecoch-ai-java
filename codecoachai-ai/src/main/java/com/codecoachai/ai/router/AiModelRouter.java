package com.codecoachai.ai.router;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.client.ProviderAiCaller;
import com.codecoachai.ai.client.ProviderAiCaller.CallResult;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.guard.RetryGuard;
import com.codecoachai.ai.guard.TokenAccountant;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 模型路由器：
 * 1. 检查用户配额
 * 2. 选定主 provider，按重试策略调用
 * 3. 失败且开启降级，则切到 fallback provider 再试
 * 4. 累计 token 计费
 * 5. 返回结果（含 route_trace）
 *
 * 调用方建议：通过此类调用 AI，可获得"重试 + 降级 + 配额"一体能力；
 * 旧代码继续用 AiClient.chat() 也兼容。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelRouter {

    private final AiRouterProperties routerProperties;
    private final ProviderAiCaller providerAiCaller;
    private final RetryGuard retryGuard;
    private final TokenAccountant tokenAccountant;

    /**
     * 路由调用入口。
     *
     * @param ctx 调用上下文（场景、提示词、用户、模型类型、强制 provider 等）
     */
    public RouteResult chat(AiCallContext ctx) {
        if (ctx == null || !StringUtils.hasText(ctx.getPrompt())) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        // 1. 配额检查（按需，可在 ctx 关闭）
        if (Boolean.TRUE.equals(ctx.getCheckQuota())) {
            tokenAccountant.checkQuota(ctx.getUserId());
        }

        AiRouterProperties.Router routerCfg = routerProperties.getRouter();
        String primary = StringUtils.hasText(ctx.getForceProvider())
                ? ctx.getForceProvider()
                : routerCfg.getDefaultProvider();
        String fallback = Boolean.TRUE.equals(routerCfg.getFallbackEnabled())
                && !StringUtils.hasText(ctx.getForceProvider())
                ? routerCfg.getFallbackProvider()
                : null;

        String modelType = StringUtils.hasText(ctx.getModelType()) ? ctx.getModelType() : "chat";
        StringBuilder routeTrace = new StringBuilder();

        // 2. 主调用 + 重试
        try {
            routeTrace.append(primary);
            CallResult result = retryGuard.execute("ai-router:" + ctx.getScene() + ":" + primary,
                    () -> providerAiCaller.chat(primary, ctx.getPrompt(), modelType));
            return toRouteResult(result, routeTrace.toString(), ctx);
        } catch (AiProviderException primaryEx) {
            if (StringUtils.hasText(fallback)) {
                log.warn("主 provider [{}] 失败 ({})，尝试降级到 [{}]", primary, primaryEx.getFailureType(), fallback);
                routeTrace.append(" -> ").append(fallback);
                try {
                    CallResult result = retryGuard.execute("ai-router:" + ctx.getScene() + ":" + fallback,
                            () -> providerAiCaller.chat(fallback, ctx.getPrompt(), modelType));
                    return toRouteResult(result, routeTrace.toString(), ctx);
                } catch (AiProviderException fallbackEx) {
                    // 失败回退分钟配额
                    tokenAccountant.rollbackMinuteCount(ctx.getUserId());
                    throw fallbackEx;
                }
            }
            tokenAccountant.rollbackMinuteCount(ctx.getUserId());
            throw primaryEx;
        }
    }

    private RouteResult toRouteResult(CallResult call, String trace, AiCallContext ctx) {
        tokenAccountant.accumulate(
                ctx.getUserId(),
                call.getPromptTokens() == null ? 0 : call.getPromptTokens(),
                call.getCompletionTokens() == null ? 0 : call.getCompletionTokens(),
                call.getEstimatedCost() == null ? 0 : call.getEstimatedCost()
        );
        RouteResult r = new RouteResult();
        r.setContent(call.getContent());
        r.setProvider(call.getProvider());
        r.setModel(call.getModel());
        r.setPromptTokens(call.getPromptTokens());
        r.setCompletionTokens(call.getCompletionTokens());
        r.setTotalTokens(call.getTotalTokens());
        r.setEstimatedCost(call.getEstimatedCost());
        r.setElapsedMs(call.getElapsedMs());
        r.setRouteTrace(trace);
        return r;
    }

    // ========== DTO ==========

    @Data
    public static class AiCallContext {
        /** 场景标识（用于日志、限流维度），例：interview.evaluate / resume.parse */
        private String scene;
        /** 完整 prompt */
        private String prompt;
        /** 用户 ID（用于配额） */
        private Long userId;
        /** chat / reasoner */
        private String modelType = "chat";
        /** 强制使用某 provider（管理员测试用）；null 走默认 */
        private String forceProvider;
        /** 是否检查配额；默认 true */
        private Boolean checkQuota = true;
    }

    @Data
    public static class RouteResult {
        private String content;
        private String provider;
        private String model;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Double estimatedCost;
        private Long elapsedMs;
        private Long aiCallLogId;
        /** 路由轨迹，例：deepseek 或 deepseek -> dashscope */
        private String routeTrace;
    }
}
