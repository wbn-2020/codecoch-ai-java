package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AI 调用日志增强服务。
 * 封装 AiModelRouter 调用 + 自动写入 ai_call_log（含 route_trace / token_cost）。
 *
 * 推荐新业务代码使用此类代替直接调用 AiModelRouter，以获得完整的日志记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogService {

    private static final String MDC_TRACE_ID = "traceId";

    private final AiModelRouter aiModelRouter;
    private final AiCallLogMapper aiCallLogMapper;

    /**
     * 调用 AI 并自动记录日志。
     */
    public RouteResult callAndLog(AiCallContext ctx) {
        long start = System.currentTimeMillis();
        RouteResult result = null;
        Exception error = null;

        try {
            result = aiModelRouter.chat(ctx);
            return result;
        } catch (Exception ex) {
            error = ex;
            throw ex;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            saveLog(ctx, result, error, elapsed);
        }
    }

    private void saveLog(AiCallContext ctx, RouteResult result, Exception error, long elapsed) {
        try {
            AiCallLog logEntry = new AiCallLog();
            logEntry.setUserId(ctx.getUserId());
            logEntry.setScene(ctx.getScene());
            logEntry.setBusinessId(ctx.getBusinessId());
            logEntry.setRequestId(StringUtils.hasText(ctx.getRequestId()) ? ctx.getRequestId() : UUID.randomUUID().toString());
            logEntry.setTraceId(currentTraceId());
            logEntry.setPromptTemplateId(ctx.getPromptTemplateId());
            logEntry.setPromptTemplateVersionId(ctx.getPromptTemplateVersionId());
            logEntry.setPromptVersion(ctx.getPromptVersion());
            logEntry.setInputVariablesJson(ctx.getInputVariablesJson());
            logEntry.setModelParamsJson(ctx.getModelParamsJson());
            logEntry.setPromptHash(ctx.getPromptHash());
            logEntry.setResponseFormat(StringUtils.hasText(ctx.getResponseFormat()) ? ctx.getResponseFormat() : "TEXT");
            logEntry.setRequestPrompt(truncate(ctx.getPrompt(), 10000));
            logEntry.setRequestBody(truncate(ctx.getRequestBody(), 10000));

            if (result != null) {
                logEntry.setModelName(result.getModel());
                logEntry.setModel(result.getModel());
                logEntry.setResponseContent(truncate(result.getContent(), 10000));
                logEntry.setResponseBody(truncate(result.getContent(), 10000));
                logEntry.setPromptTokens(result.getPromptTokens());
                logEntry.setCompletionTokens(result.getCompletionTokens());
                logEntry.setTotalTokens(result.getTotalTokens());
                logEntry.setRouteTrace(result.getRouteTrace());
                logEntry.setEstimatedCost(result.getEstimatedCost());
                logEntry.setTokenCost(result.getEstimatedCost());
                logEntry.setSuccess(1);
                logEntry.setStatus(1);
            } else {
                logEntry.setSuccess(0);
                logEntry.setStatus(0);
            }

            if (error != null) {
                logEntry.setSuccess(0);
                logEntry.setStatus(0);
                logEntry.setErrorMessage(truncate(error.getMessage(), 2000));
            }

            logEntry.setElapsedMs(elapsed);
            logEntry.setCostMillis(elapsed);
            aiCallLogMapper.insert(logEntry);
            if (result != null) {
                result.setAiCallLogId(logEntry.getId());
            }
        } catch (Exception ex) {
            log.warn("AI 调用日志写入失败 scene={}", ctx.getScene(), ex);
        }
    }

    private String currentTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId != null) {
            return traceId;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HeaderConstants.TRACE_ID);
        }
        return null;
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }
}
