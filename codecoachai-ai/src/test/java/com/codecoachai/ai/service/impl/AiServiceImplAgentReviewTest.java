package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.GenerateAgentReviewDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplAgentReviewTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private PromptRenderService promptRenderService;
    @Mock
    private AiCallLogService aiCallLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiProperties aiProperties;
    private AiServiceImpl service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setMockEnabled(false);
        aiProperties.setProvider("openai-compatible");
        aiProperties.setModel("deepseek-chat");
        when(promptRenderService.render(any(String.class), any(String.class), anyMap()))
                .thenAnswer(invocation -> PromptRenderResult.builder()
                        .scene(invocation.getArgument(0))
                        .renderedPrompt("rendered prompt")
                        .inputVariablesJson("{}")
                        .modelParamsJson("{}")
                        .promptHash("hash")
                        .fallbackUsed(false)
                        .build());
        service = new AiServiceImpl(
                aiCallLogMapper,
                promptRenderService,
                aiCallLogService,
                aiProperties,
                objectMapper);
    }

    @Test
    void generateAgentReviewReturnsCompleteMockAndTruncatesTasks() throws Exception {
        aiProperties.setMockEnabled(true);
        doAnswer(invocation -> {
            AiCallLog log = invocation.getArgument(0);
            log.setId(801L);
            return 1;
        }).when(aiCallLogMapper).insert(any(AiCallLog.class));

        GenerateAgentReviewDTO dto = reviewDTO(31);
        dto.setTasks(new ArrayList<>(dto.getTasks().subList(0, 30)));
        var result = service.generateAgentReview(dto);

        assertNotNull(result.getSummary());
        assertFalse(result.getFacts().isEmpty());
        assertFalse(result.getLimits().isEmpty());
        assertFalse(result.getDriftReasons().isEmpty());
        assertFalse(result.getAdjustments().isEmpty());
        assertFalse(result.getNextActions().isEmpty());
        assertEquals(801L, result.getAiCallLogId());
        assertNotNull(result.getRawResponse());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptRenderService).render(
                org.mockito.ArgumentMatchers.eq("AGENT_REVIEW_GENERATE"),
                promptCaptor.capture(),
                variablesCaptor.capture());
        Map<String, String> variables = variablesCaptor.getValue();
        JsonNode tasks = objectMapper.readTree(variables.get("tasksJson"));
        assertEquals(30, tasks.size());
        assertEquals("任务 30", tasks.get(29).path("title").asText());
        assertEquals("30", variables.get("providedTaskCount"));
        assertEquals("30", variables.get("displayedTaskCount"));
        assertEquals("true", variables.get("tasksTruncated"));
        assertTrue(variables.get("taskDisplayNotice").contains("仅展示前 30 条部分任务"));
        assertTrue(promptCaptor.getValue().contains("不得编造录用通知、面试通过"));
        assertTrue(promptCaptor.getValue().contains("样本不足，结论仅为弱调整信号"));
        assertTrue(promptCaptor.getValue().contains("所有给用户看的内容必须使用中文"));
    }

    @Test
    void generateAgentReviewParsesCodeFenceAndDefaultsMissingFields() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                ```json
                {
                  "summary": "今天完成了核心练习。",
                  "facts": ["完成 Redis 练习"],
                  "drifts": ["待处理任务仍未闭环"]
                }
                ```
                """);
        routeResult.setAiCallLogId(902L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = service.generateAgentReview(reviewDTO(1));

        assertEquals("今天完成了核心练习。", result.getSummary());
        assertEquals(List.of("完成 Redis 练习"), result.getFacts());
        assertEquals(List.of("待处理任务仍未闭环"), result.getDriftReasons());
        assertTrue(result.getLimits().isEmpty());
        assertTrue(result.getAdjustments().isEmpty());
        assertTrue(result.getNextActions().isEmpty());
        assertEquals(902L, result.getAiCallLogId());
        assertTrue(result.getRawResponse().contains("```json"));
    }

    @Test
    void generateAgentReviewAcceptsDriftReasonsAliasAndScalarListValue() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "summary": "今日完成了既定任务。",
                  "facts": "完成一项 Java 练习",
                  "driftReasons": ["计划没有明显偏移"],
                  "adjustments": [],
                  "nextActions": ["继续完成下一项任务"]
                }
                """);
        routeResult.setAiCallLogId(903L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = service.generateAgentReview(reviewDTO(1));

        assertEquals(List.of("完成一项 Java 练习"), result.getFacts());
        assertEquals(List.of("计划没有明显偏移"), result.getDriftReasons());
        assertTrue(result.getLimits().isEmpty());
    }

    @Test
    void generateAgentReviewMockMarksLowSampleAsWeakSignal() {
        aiProperties.setMockEnabled(true);

        GenerateAgentReviewDTO dto = reviewDTO(1);
        dto.setDoneCount(0);
        dto.setTodoCount(1);
        var result = service.generateAgentReview(dto);

        assertTrue(result.getLimits().contains("样本不足，结论仅为弱调整信号。"));
        assertTrue(result.getSummary().contains("尚无已完成记录"));
        assertFalse(result.getSummary().contains("推进保持稳定"));
    }

    @Test
    void generateAgentReviewLogsAndThrowsBusinessExceptionWhenProviderFails() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenThrow(new AiProviderException(AiFailureType.TIMEOUT, "provider timeout"));

        assertThrows(BusinessException.class, () -> service.generateAgentReview(reviewDTO(1)));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    private GenerateAgentReviewDTO reviewDTO(int taskSize) {
        GenerateAgentReviewDTO dto = new GenerateAgentReviewDTO();
        dto.setUserId(10L);
        dto.setTargetJobId(20L);
        dto.setReviewDate("2026-07-17");
        dto.setTaskCount(taskSize);
        dto.setDoneCount(Math.min(taskSize, 2));
        dto.setSkippedCount(0);
        dto.setTodoCount(Math.max(taskSize - 2, 0));
        dto.setCompletionRate(new BigDecimal("0.50"));
        dto.setAgentSuccessRate(new BigDecimal("0.80"));
        dto.setReadinessScore(72);
        dto.setTopSkills(List.of("Java", "Redis"));
        List<GenerateAgentReviewDTO.TaskBrief> tasks = new ArrayList<>();
        for (int i = 1; i <= taskSize; i++) {
            GenerateAgentReviewDTO.TaskBrief task = new GenerateAgentReviewDTO.TaskBrief();
            task.setTitle("任务 " + i);
            task.setStatus(i <= 2 ? "DONE" : "TODO");
            task.setSkill(i % 2 == 0 ? "Redis" : "Java");
            tasks.add(task);
        }
        dto.setTasks(tasks);
        return dto;
    }
}
