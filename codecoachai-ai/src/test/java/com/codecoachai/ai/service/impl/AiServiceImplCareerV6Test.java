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
import com.codecoachai.ai.domain.dto.GenerateAgentWeeklyReportDTO;
import com.codecoachai.ai.domain.dto.GenerateApplicationEventReviewDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewPreparationDTO;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplCareerV6Test {

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
    void applicationEventReviewMockUsesFactRefsAndMasksPii() throws Exception {
        enableMockWithLogId(701L);
        GenerateApplicationEventReviewDTO dto = applicationReviewDTO("INTERVIEW_COMPLETED");
        dto.getFacts().get(0).setContent("联系邮箱 user@example.com，手机 13812341234；项目异常链路没有讲完整。");

        var result = service.generateApplicationEventReview(dto);

        assertNotNull(result.getSummary());
        assertEquals(List.of("U1"), result.getSignals().get(0).getFactRefs());
        assertEquals("MEDIUM", result.getSignals().get(0).getConfidenceLevel());
        assertEquals(701L, result.getAiCallLogId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(promptRenderService).render(
                org.mockito.ArgumentMatchers.eq("APPLICATION_EVENT_REVIEW_GENERATE"),
                prompt.capture(),
                variables.capture());
        JsonNode facts = objectMapper.readTree(variables.getValue().get("factsJson"));
        assertTrue(facts.get(0).path("content").asText().contains("***@example.com"));
        assertTrue(facts.get(0).path("content").asText().contains("138****1234"));
        assertTrue(prompt.getValue().contains("不得推断招聘方内部原因"));
        assertTrue(prompt.getValue().contains("必须使用中文"));
    }

    @Test
    void applicationEventReviewParsesCodeFenceAndDefaultsInvalidFieldTypes() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                ```json
                {
                  "summary": "只能确认当前记录中的事实。",
                  "limits": "缺少外部直接反馈。",
                  "signals": [{"content":"值得复练","factRefs":["U1"],"confidenceLevel":"UNKNOWN"}],
                  "adjustments": 1,
                  "nextActions": ["完成一次复述"]
                }
                ```
                """);
        routeResult.setAiCallLogId(702L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = service.generateApplicationEventReview(applicationReviewDTO("INTERVIEW_COMPLETED"));

        assertEquals(List.of("缺少外部直接反馈。"), result.getLimits());
        assertEquals(1, result.getSignals().size());
        assertEquals(null, result.getSignals().get(0).getConfidenceLevel());
        assertTrue(result.getAdjustments().isEmpty());
        assertEquals(List.of("完成一次复述"), result.getNextActions());
        assertEquals(702L, result.getAiCallLogId());
    }

    @Test
    void interviewPreparationMockHonorsThirtyMinuteBudgetAndLowEvidence() {
        enableMockWithLogId(711L);
        GenerateInterviewPreparationDTO dto = interviewPreparationDTO();
        dto.setApplicationId(null);
        dto.setTimeBudgetMinutes(30);
        dto.setSourceHash("source-hash");

        var result = service.generateInterviewPreparation(dto);

        assertEquals(3, result.getFocusAreas().size());
        assertEquals(3, result.getPracticeQuestions().size());
        assertEquals("LOW", result.getConfidenceLevel());
        assertFalse(result.getFallback());
        assertEquals("source-hash", result.getSourceHash());
        assertTrue(result.getLimits().stream().anyMatch(item -> item.contains("未关联投递")));
        assertTrue(result.getPracticeQuestions().stream().allMatch(item -> item.startsWith("建议练习方向")));
    }

    @Test
    void interviewPreparationParsesCodeFenceAndRejectsUnsupportedEventType() {
        GenerateInterviewPreparationDTO invalid = interviewPreparationDTO();
        invalid.setEventType("FOLLOW_UP");
        assertThrows(BusinessException.class, () -> service.generateInterviewPreparation(invalid));

        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                ```json
                {"summary":"准备包摘要","facts":["日历记录已确认"],"confidenceLevel":"MEDIUM"}
                ```
                """);
        routeResult.setAiCallLogId(712L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = service.generateInterviewPreparation(interviewPreparationDTO());

        assertEquals("准备包摘要", result.getSummary());
        assertEquals(List.of("日历记录已确认"), result.getFacts());
        assertTrue(result.getFocusAreas().isEmpty());
        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertFalse(result.getFallback());
    }

    @Test
    void weeklyReportMockOnlyReturnsAllowedHypotheses() {
        enableMockWithLogId(721L);
        GenerateAgentWeeklyReportDTO dto = weeklyReportDTO();

        var result = service.generateWeeklyCareerReport(dto);

        assertNotNull(result.getSummary());
        assertEquals(List.of("本周完成投递：4 次"), result.getFactNarrative());
        assertEquals(1, result.getHypotheses().size());
        assertEquals("SUG-1", result.getHypotheses().get(0).getHypothesisId());
        assertEquals("channel", result.getHypotheses().get(0).getPrimaryVariable());
        assertEquals("PROPOSED", result.getHypotheses().get(0).getStatus());
        assertEquals(721L, result.getAiCallLogId());
    }

    @Test
    void weeklyReportFiltersInventedHypothesisAndParsesCodeFence() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                ```json
                {
                  "summary":"本周仅形成弱观察。",
                  "factNarrative":["本周完成投递：4 次"],
                  "signalNarrative":[],
                  "hypotheses":[
                    {"hypothesisId":"INVENTED","statement":"自动投递更多岗位","primaryVariable":"channel"},
                    {"hypothesisId":"SUG-1","statement":"擅自改写实验","primaryVariable":"resume_version",
                     "minimumSample":1,"observationDays":1,"confidenceLevel":"HIGH"}
                  ],
                  "limits":["样本不足。"]
                }
                ```
                """);
        routeResult.setAiCallLogId(722L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = service.generateWeeklyCareerReport(weeklyReportDTO());

        assertEquals(1, result.getHypotheses().size());
        assertEquals("SUG-1", result.getHypotheses().get(0).getHypothesisId());
        assertEquals("固定岗位和简历版本，观察渠道变化。", result.getHypotheses().get(0).getStatement());
        assertEquals("channel", result.getHypotheses().get(0).getPrimaryVariable());
        assertEquals(10, result.getHypotheses().get(0).getMinimumSample());
        assertEquals(14, result.getHypotheses().get(0).getObservationDays());
        assertEquals("LOW", result.getHypotheses().get(0).getConfidenceLevel());
        assertEquals(List.of("样本不足。"), result.getLimits());
    }

    @Test
    void newCareerScenesSaveFailureLogAndThrowBusinessException() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenThrow(new AiProviderException(AiFailureType.TIMEOUT, "provider timeout"));

        assertThrows(BusinessException.class,
                () -> service.generateApplicationEventReview(applicationReviewDTO("REJECTION")));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    private void enableMockWithLogId(Long id) {
        aiProperties.setMockEnabled(true);
        doAnswer(invocation -> {
            AiCallLog log = invocation.getArgument(0);
            log.setId(id);
            return 1;
        }).when(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    private GenerateApplicationEventReviewDTO applicationReviewDTO(String scenario) {
        GenerateApplicationEventReviewDTO dto = new GenerateApplicationEventReviewDTO();
        dto.setUserId(10L);
        dto.setEventId(20L);
        dto.setApplicationId(30L);
        dto.setTargetJobId(40L);
        dto.setScenario(scenario);
        dto.setEventScope("REAL_JOB");
        dto.setJobTitle("Java 后端工程师");
        dto.setApplicationSource("官网");
        dto.setApplicationStatus("INTERVIEWING");
        dto.setEventType(scenario);
        dto.setEventTime("2026-07-18 14:00:00");
        dto.setEventSummary("用户记录了真实求职事件。");
        GenerateApplicationEventReviewDTO.Fact fact = new GenerateApplicationEventReviewDTO.Fact();
        fact.setId("U1");
        fact.setContent("项目异常链路没有讲完整。");
        fact.setOwner("USER");
        fact.setSourceType("USER_OBSERVATION");
        dto.setFacts(List.of(fact));
        dto.setSelfReflection("下一轮需要更完整地说明补偿流程。");
        dto.setConfidenceCeiling("MEDIUM");
        return dto;
    }

    private GenerateInterviewPreparationDTO interviewPreparationDTO() {
        GenerateInterviewPreparationDTO dto = new GenerateInterviewPreparationDTO();
        dto.setUserId(10L);
        dto.setCalendarEventId(50L);
        dto.setApplicationId(30L);
        dto.setTimeBudgetMinutes(60);
        dto.setEventTitle("Java 后端一面");
        dto.setEventDescription("线上面试");
        dto.setEventType("INTERVIEW");
        dto.setEventLocalTime("2026-07-20 14:00:00");
        dto.setTimezone("Asia/Shanghai");
        dto.setCompanyName("示例公司");
        dto.setJobTitle("Java 后端工程师");
        dto.setJobRequirements(List.of("Redis 缓存一致性", "Spring Boot"));
        dto.setProjectEvidence(List.of("已有订单系统缓存治理项目证据", "已有接口幂等项目证据"));
        dto.setReadinessGaps(List.of("Redis 场景表达", "故障恢复说明", "项目取舍说明"));
        dto.setRecentInterviewWeaknesses(List.of("异常链路表达"));
        dto.setSourceHash("hash");
        return dto;
    }

    private GenerateAgentWeeklyReportDTO weeklyReportDTO() {
        GenerateAgentWeeklyReportDTO dto = new GenerateAgentWeeklyReportDTO();
        dto.setUserId(10L);
        dto.setWeekStartDate("2026-07-13");
        dto.setWeekEndDate("2026-07-19");
        dto.setTargetScopeKey("TARGET_JOB:40");
        dto.setTimezone("Asia/Shanghai");

        GenerateAgentWeeklyReportDTO.WeeklyFact fact = new GenerateAgentWeeklyReportDTO.WeeklyFact();
        fact.setFactId("FACT-1");
        fact.setFactType("APPLICATION_COUNT");
        fact.setLabel("本周完成投递");
        fact.setValue(4);
        fact.setUnit("次");
        fact.setSourceRefs(List.of("application:1"));
        dto.setFacts(List.of(fact));

        GenerateAgentWeeklyReportDTO.WeeklySignal signal = new GenerateAgentWeeklyReportDTO.WeeklySignal();
        signal.setSignalId("SIG-1");
        signal.setSignalType("CHANNEL_OBSERVATION");
        signal.setTitle("渠道样本不足");
        signal.setDescription("当前只能形成弱观察。");
        signal.setConfidenceLevel("LOW");
        signal.setSourceRefs(List.of("application:1"));
        dto.setSignals(List.of(signal));
        dto.setLimits(List.of("样本不足，当前只形成弱观察。"));

        GenerateAgentWeeklyReportDTO.AllowedSuggestion suggestion =
                new GenerateAgentWeeklyReportDTO.AllowedSuggestion();
        suggestion.setSuggestionId("SUG-1");
        suggestion.setTitle("观察渠道变化");
        suggestion.setStatement("固定岗位和简历版本，观察渠道变化。");
        suggestion.setPrimaryVariable("channel");
        suggestion.setFixedVariables(List.of("target_job", "resume_version"));
        suggestion.setExpectedSignal("有效反馈数量变化");
        suggestion.setSuccessMetric("成熟样本中的有效反馈数");
        suggestion.setMinimumSample(10);
        suggestion.setObservationDays(14);
        suggestion.setStopCondition("达到观察期或样本门槛后复盘");
        suggestion.setConfidenceLevel("LOW");
        suggestion.setBasedOnSignalIds(List.of("SIG-1"));
        suggestion.setSourceRefs(List.of("application:1"));
        dto.setAllowedSuggestions(List.of(suggestion));
        return dto;
    }
}
