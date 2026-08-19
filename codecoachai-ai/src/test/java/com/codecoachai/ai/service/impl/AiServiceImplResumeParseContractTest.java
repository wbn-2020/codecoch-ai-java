package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplResumeParseContractTest {

    private static final String VALID_RESPONSE = """
            {
              "schemaVersion": "resume-import-v1",
              "basicInfo": {
                "name": "张三",
                "phone": "",
                "email": "",
                "location": "上海"
              },
              "targetPosition": "Java 后端开发工程师",
              "summary": "负责后端服务开发。",
              "skills": ["Java", "Spring Boot"],
              "workExperiences": [{
                "company": "示例公司",
                "position": "后端开发",
                "period": "2022-2024",
                "description": "负责核心服务。",
                "responsibilities": ["接口设计"],
                "achievements": ["稳定性治理"]
              }],
              "projectExperiences": [{
                "projectName": "面试平台",
                "period": "2023-2024",
                "background": "求职训练",
                "role": "后端开发",
                "description": "实现面试链路。",
                "techStack": ["Java"],
                "responsibilities": ["服务实现"],
                "coreFeatures": ["模拟面试"],
                "technicalDifficulties": ["幂等"],
                "optimizationResults": ["降低失败率"],
                "achievements": ["完成交付"]
              }],
              "educationExperiences": [{
                "school": "示例大学",
                "degree": "本科",
                "major": "计算机",
                "period": "2018-2022",
                "description": ""
              }]
            }
            """;

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private PromptRenderService promptRenderService;
    @Mock
    private AiCallLogService aiCallLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(promptRenderService.render(any(String.class), any(String.class), anyMap()))
                .thenAnswer(invocation -> PromptRenderResult.builder()
                        .scene(invocation.getArgument(0))
                        .renderedPrompt("rendered prompt")
                        .inputVariablesJson("{}")
                        .modelParamsJson("{}")
                        .promptHash("hash")
                        .fallbackUsed(false)
                        .build());
    }

    @Test
    void realProviderResponseAcceptsOnlyCompleteTypedSchema() throws Exception {
        AiServiceImpl service = service(false);
        RouteResult routeResult = new RouteResult();
        routeResult.setContent(VALID_RESPONSE);
        routeResult.setAiCallLogId(901L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        JsonNode result = objectMapper.readTree(service.parseResume(request()).getStructuredJson());

        assertEquals("resume-import-v1", result.path("schemaVersion").asText());
        assertTrue(result.path("workExperiences").isArray());
        assertTrue(result.path("workExperiences").get(0).isObject());
        assertTrue(result.path("projectExperiences").get(0).path("techStack").isArray());
        assertFalse(result.path("basicInfo").path("phone").isNull());
    }

    @Test
    void rejectsJsonEncodedArraysAndObjects() {
        AiServiceImpl service = service(false);
        RouteResult routeResult = new RouteResult();
        routeResult.setContent(VALID_RESPONSE.replace(
                "\"skills\": [\"Java\", \"Spring Boot\"]",
                "\"skills\": \"[\\\"Java\\\",\\\"Spring Boot\\\"]\""));
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        assertThrows(BusinessException.class, () -> service.parseResume(request()));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        AiServiceImpl service = service(false);
        RouteResult routeResult = new RouteResult();
        routeResult.setContent(VALID_RESPONSE.replace("resume-import-v1", "resume-import-v0"));
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        assertThrows(BusinessException.class, () -> service.parseResume(request()));
    }

    @Test
    void mockResponseUsesTheSameParserContractAndKeepsContactsEmpty() throws Exception {
        AiServiceImpl service = service(true);

        JsonNode result = objectMapper.readTree(service.parseResume(request()).getStructuredJson());

        assertEquals("resume-import-v1", result.path("schemaVersion").asText());
        assertEquals("", result.path("basicInfo").path("phone").asText());
        assertEquals("", result.path("basicInfo").path("email").asText());
        assertTrue(result.path("projectExperiences").get(0).path("responsibilities").isArray());
    }

    @Test
    void promptDeclaresFixedSchemaAndMaskedContactRule() {
        AiServiceImpl service = service(true);

        service.parseResume(request());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptRenderService).render(eq("RESUME_STRUCTURED_PARSE"), promptCaptor.capture(), anyMap());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("\"resume-import-v1\""));
        assertTrue(prompt.contains("脱敏值"));
        assertTrue(prompt.contains("不得把数组或对象序列化成 JSON 字符串"));
        assertTrue(prompt.contains("projectExperiences"));
        assertTrue(prompt.contains("educationExperiences"));
    }

    private AiServiceImpl service(boolean mockEnabled) {
        AiProperties properties = new AiProperties();
        properties.setMockEnabled(mockEnabled);
        properties.setProvider("openai-compatible");
        properties.setModel("deepseek-chat");
        return new AiServiceImpl(
                aiCallLogMapper,
                promptRenderService,
                aiCallLogService,
                properties,
                objectMapper);
    }

    private ParseResumeDTO request() {
        ParseResumeDTO dto = new ParseResumeDTO();
        dto.setAnalysisRecordId(100L);
        dto.setUserId(10L);
        dto.setRawText("张三，Java 后端开发，联系方式已脱敏。");
        dto.setOriginalFilename("resume.docx");
        dto.setFileExt("docx");
        return dto;
    }
}
