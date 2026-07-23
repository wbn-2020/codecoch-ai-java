package com.codecoachai.ai.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.security.SensitiveTextMasker;
import org.junit.jupiter.api.Test;

class AiConvertTest {

    @Test
    void highSensitivityLogDoesNotExposeRawOrPreviewByDefault() {
        AiCallLog log = sampleLog("AGENT_DAILY_PLAN");

        AiCallLogVO vo = AiConvert.toLogVO(log);

        assertNull(vo.getInputVariablesJson());
        assertNull(vo.getRequestPrompt());
        assertNull(vo.getResponseContent());
        assertNull(vo.getRequestBody());
        assertNull(vo.getResponseBody());
        assertNull(vo.getRequestPromptPreview());
        assertNull(vo.getResponseContentPreview());
        assertFalse(vo.getRawFieldsIncluded());
        assertTrue(vo.getRawFieldsAvailable());
        assertEquals("admin:ai:log:raw:view", vo.getRawAccessPermission());
        assertNotNull(vo.getRequestPromptHash());
        assertNotNull(vo.getResponseContentHash());
    }

    @Test
    void rawLogAccessIncludesRawFieldsOnlyWhenExplicitlyRequested() {
        AiCallLog log = sampleLog("AGENT_DAILY_PLAN");

        AiCallLogVO vo = AiConvert.toLogVO(log, true);

        assertEquals(log.getInputVariablesJson(), vo.getInputVariablesJson());
        assertEquals(log.getRequestPrompt(), vo.getRequestPrompt());
        assertEquals(log.getResponseContent(), vo.getResponseContent());
        assertEquals(log.getRequestBody(), vo.getRequestBody());
        assertEquals(log.getResponseBody(), vo.getResponseBody());
        assertTrue(vo.getRawFieldsIncluded());
    }

    @Test
    void promptTemplateDoesNotExposeRawContentByDefault() {
        PromptTemplate template = samplePromptTemplate();

        PromptTemplateVO vo = AiConvert.toPromptVO(template);

        assertNull(vo.getContent());
        assertNull(vo.getTemplateContent());
        assertEquals(template.getContent().length(), vo.getContentLength());
        assertNotNull(vo.getContentHash());
        assertTrue(vo.getRawFieldsAvailable());
        assertFalse(vo.getRawFieldsIncluded());
        assertEquals("admin:ai:prompt:raw:view", vo.getRawAccessPermission());
    }

    @Test
    void promptTemplateRawAccessIncludesContentOnlyWhenExplicitlyRequested() {
        PromptTemplate template = samplePromptTemplate();

        PromptTemplateVO vo = AiConvert.toPromptVO(template, true);

        assertEquals(template.getContent(), vo.getContent());
        assertEquals(template.getTemplateContent(), vo.getTemplateContent());
        assertTrue(vo.getRawFieldsIncluded());
    }

    @Test
    void promptVersionDoesNotExposeRawContentOrModelParamsByDefault() {
        PromptTemplateVersion version = samplePromptTemplateVersion();

        PromptTemplateVersionVO vo = AiConvert.toVersionVO(version);

        assertNull(vo.getContent());
        assertNull(vo.getModelParamsJson());
        assertEquals(version.getContent().length(), vo.getContentLength());
        assertNotNull(vo.getContentHash());
        assertNotNull(vo.getModelParamsHash());
        assertTrue(vo.getRawFieldsAvailable());
        assertFalse(vo.getRawFieldsIncluded());
        assertEquals("admin:ai:prompt:raw:view", vo.getRawAccessPermission());
    }

    @Test
    void promptVersionRawAccessIncludesContentOnlyWhenExplicitlyRequested() {
        PromptTemplateVersion version = samplePromptTemplateVersion();

        PromptTemplateVersionVO vo = AiConvert.toVersionVO(version, true);

        assertEquals(version.getContent(), vo.getContent());
        assertEquals(version.getModelParamsJson(), vo.getModelParamsJson());
        assertTrue(vo.getRawFieldsIncluded());
    }

    @Test
    void errorMessageAndPreviewMaskPiiAndBearerTokens() {
        AiCallLog log = sampleLog("STATUS_CHECK");
        log.setErrorMessage("email alice@example.com phone 13812345678 token=secret-token Authorization: Bearer sk-live-123");

        AiCallLogVO vo = AiConvert.toLogVO(log);
        String preview = SensitiveTextMasker.safePreview("Authorization: Bearer sk-live-123 token=secret-token alice@example.com 13812345678");

        assertFalse(vo.getErrorMessage().contains("alice@example.com"));
        assertFalse(vo.getErrorMessage().contains("13812345678"));
        assertFalse(vo.getErrorMessage().contains("secret-token"));
        assertFalse(vo.getErrorMessage().contains("sk-live-123"));
        assertTrue(vo.getErrorMessage().contains("***@***"));
        assertTrue(vo.getErrorMessage().contains("1**********"));
        assertFalse(preview.contains("sk-live-123"));
        assertFalse(preview.contains("secret-token"));
    }

    @Test
    void logVoMarksPrimaryProviderResultAsLlm() {
        AiCallLog log = sampleLog("AGENT_DAILY_PLAN");
        log.setModelName("deepseek-chat");
        log.setRouteTrace("deepseek");

        AiCallLogVO vo = AiConvert.toLogVO(log);

        assertEquals("LLM", vo.getResultSource());
        assertEquals("真实模型", vo.getResultSourceLabel());
        assertFalse(vo.getFallback());
    }

    @Test
    void logVoMarksFallbackRouteAsFallback() {
        AiCallLog log = sampleLog("AGENT_DAILY_PLAN");
        log.setModelName("qwen-plus");
        log.setRouteTrace("deepseek -> dashscope");

        AiCallLogVO vo = AiConvert.toLogVO(log);

        assertEquals("FALLBACK", vo.getResultSource());
        assertEquals("降级兜底", vo.getResultSourceLabel());
        assertTrue(vo.getFallback());
    }

    @Test
    void logVoMarksMockModelAsMock() {
        AiCallLog log = sampleLog("PROMPT_VERSION_TEST");
        log.setModelName("deepseek-chat(mock)");
        log.setRequestBody("{\"mockMode\":true}");

        AiCallLogVO vo = AiConvert.toLogVO(log);

        assertEquals("MOCK", vo.getResultSource());
        assertEquals("模拟数据", vo.getResultSourceLabel());
        assertFalse(vo.getFallback());
    }

    @Test
    void logVoMarksRuleResultAsRule() {
        AiCallLog log = sampleLog("AGENT_REVIEW");
        log.setModelName("rule-engine");
        log.setRouteTrace("rule");

        AiCallLogVO vo = AiConvert.toLogVO(log);

        assertEquals("RULE", vo.getResultSource());
        assertEquals("规则结果", vo.getResultSourceLabel());
        assertFalse(vo.getFallback());
    }

    @Test
    void logVoDoesNotAssumeLlmWhenProvenanceIsMissing() {
        AiCallLog log = sampleLog("STATUS_CHECK");

        AiCallLogVO vo = AiConvert.toLogVO(log);

        assertEquals("UNKNOWN", vo.getResultSource());
        assertEquals("未知来源", vo.getResultSourceLabel());
        assertFalse(vo.getFallback());
    }

    private AiCallLog sampleLog(String scene) {
        AiCallLog log = new AiCallLog();
        log.setId(1L);
        log.setUserId(10L);
        log.setScene(scene);
        log.setInputVariablesJson("{\"resume\":\"13812345678 alice@example.com\"}");
        log.setRequestPrompt("prompt with resume and token=secret-token");
        log.setResponseContent("response with private interview feedback");
        log.setRequestBody("request-body Authorization: Bearer sk-live-123");
        log.setResponseBody("response-body");
        return log;
    }

    private PromptTemplate samplePromptTemplate() {
        PromptTemplate template = new PromptTemplate();
        template.setId(2L);
        template.setScene("INTERVIEW");
        template.setName("Interview prompt");
        template.setContent("System prompt with private rubric");
        template.setTemplateContent("User prompt with {{resume}} and {{jd}}");
        template.setStatus(1);
        return template;
    }

    private PromptTemplateVersion samplePromptTemplateVersion() {
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setId(3L);
        version.setTemplateId(2L);
        version.setScene("INTERVIEW");
        version.setVersionCode("v1");
        version.setContent("Version prompt with private rubric");
        version.setModelParamsJson("{\"temperature\":0.2,\"secret\":\"internal\"}");
        version.setStatus("ENABLED");
        return version;
    }
}
