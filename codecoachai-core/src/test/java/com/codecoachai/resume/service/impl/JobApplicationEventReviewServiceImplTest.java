package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobApplicationEventReviewGenerateDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.dto.GenerateApplicationEventReviewAiDTO;
import com.codecoachai.resume.feign.vo.GenerateApplicationEventReviewAiVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.service.support.JobApplicationEventReviewJsonCodec;
import com.codecoachai.resume.service.support.JobApplicationEventReviewPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobApplicationEventReviewServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private JobApplicationEventMapper eventMapper;
    @Mock
    private AiFeignClient aiFeignClient;

    private ObjectMapper objectMapper;
    private JobApplicationEventReviewJsonCodec codec;
    private JobApplicationEventReviewPolicy policy;
    private JobApplicationEventReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("review-user")
                .build());
        objectMapper = new ObjectMapper().findAndRegisterModules();
        codec = new JobApplicationEventReviewJsonCodec(objectMapper);
        policy = new JobApplicationEventReviewPolicy(objectMapper);
        service = new JobApplicationEventReviewServiceImpl(
                applicationMapper, eventMapper, aiFeignClient, codec, policy);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsApplicationOwnedByAnotherUserBeforeAiCall() {
        JobApplication application = application(88L, 99L);
        when(applicationMapper.selectById(88L)).thenReturn(application);

        assertThrows(BusinessException.class,
                () -> service.generate(88L, 900L, new JobApplicationEventReviewGenerateDTO()));

        verify(eventMapper, never()).selectById(anyLong());
        verify(aiFeignClient, never()).generateApplicationEventReview(any());
    }

    @Test
    void rejectsUnsupportedEventWithoutMutatingReviewJson() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(900L, 88L, "NOTE", null);
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(900L)).thenReturn(event);

        assertThrows(BusinessException.class,
                () -> service.generate(88L, 900L, new JobApplicationEventReviewGenerateDTO()));

        verify(eventMapper, never()).compareAndSetReviewJson(anyLong(), anyLong(), anyLong(), any(), any());
        verify(aiFeignClient, never()).generateApplicationEventReview(any());
    }

    @Test
    void rejectsMalformedHistoricalReviewJsonWithoutAiOrDatabaseMutation() {
        JobApplication application = application(88L, USER_ID);
        String malformedReviewJson = "{\"reportId\":55";
        JobApplicationEvent event = event(
                905L,
                88L,
                "INTERVIEW_COMPLETED",
                malformedReviewJson);
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(905L)).thenReturn(event);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.generate(88L, 905L, new JobApplicationEventReviewGenerateDTO()));

        assertEquals("历史投递事件复盘数据格式异常，请先修复后再生成", exception.getMessage());
        assertEquals(malformedReviewJson, event.getReviewJson());
        verify(aiFeignClient, never()).generateApplicationEventReview(any());
        verify(eventMapper, never()).compareAndSetReviewJson(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void fallsBackWhenAiOutputIsOnlyEnglish() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(906L, 88L, "INTERVIEW_COMPLETED", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(906L)).thenReturn(event);
        AtomicReference<String> storedJson = stubReviewJsonCas(event);

        GenerateApplicationEventReviewAiVO ai = new GenerateApplicationEventReviewAiVO();
        ai.setSummary("The recruiter approved the application.");
        ai.setLimits(List.of("There is no material limitation."));
        ai.setAdjustments(List.of("Improve the interview answer."));
        ai.setNextActions(List.of("Send another application automatically."));
        ai.setSignals(List.of(signal(
                "The candidate passed the interview.",
                List.of("S1"))));
        ai.setAiCallLogId(81L);
        when(aiFeignClient.generateApplicationEventReview(any())).thenReturn(Result.success(ai));

        JobApplicationEventStructuredReviewVO result =
                service.generate(88L, 906L, new JobApplicationEventReviewGenerateDTO());

        assertEquals("RULE", result.getAnalysis().getOwner());
        assertEquals("FALLBACK", result.getGeneration().getStatus());
        assertTrue(result.getGeneration().getFallback());
        assertEquals("AI_INVALID_OUTPUT", result.getGeneration().getFallbackReason());
        assertFalse(storedJson.get().contains("The recruiter"));
        assertFalse(storedJson.get().contains("automatically"));
    }

    @Test
    void fallsBackWhenAiOutputExceedsContractBounds() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(907L, 88L, "INTERVIEW_COMPLETED", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(907L)).thenReturn(event);
        stubReviewJsonCas(event);

        GenerateApplicationEventReviewAiVO ai = new GenerateApplicationEventReviewAiVO();
        ai.setSummary("超".repeat(501));
        ai.setLimits(List.of("限制一", "限制二", "限制三", "限制四", "限制五"));
        ai.setAdjustments(List.of("调整一", "调整二", "调整三", "调整四", "调整五"));
        ai.setNextActions(List.of("动作一", "动作二", "动作三", "动作四", "动作五"));
        ai.setAiCallLogId(82L);
        when(aiFeignClient.generateApplicationEventReview(any())).thenReturn(Result.success(ai));

        JobApplicationEventStructuredReviewVO result =
                service.generate(88L, 907L, new JobApplicationEventReviewGenerateDTO());

        assertEquals("RULE", result.getAnalysis().getOwner());
        assertEquals("FALLBACK", result.getGeneration().getStatus());
        assertTrue(result.getGeneration().getFallback());
        assertEquals("LOW", result.getGeneration().getConfidenceLevel());
    }

    @Test
    void fallsBackWhenAiOutputContainsPiiAndUntrustedClaims() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(908L, 88L, "INTERVIEW_COMPLETED", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(908L)).thenReturn(event);
        AtomicReference<String> storedJson = stubReviewJsonCas(event);

        GenerateApplicationEventReviewAiVO ai = new GenerateApplicationEventReviewAiVO();
        ai.setSummary("请联系 user@example.com 获取招聘结果。");
        ai.setLimits(List.of("也可以拨打 13812341234 追问。"));
        ai.setAdjustments(List.of("招聘方认为你的能力不足。"));
        ai.setNextActions(List.of("系统将自动为你投递下一岗位。"));
        ai.setSignals(List.of(signal("你已通过面试。", List.of("S1"))));
        ai.setRawResponse("包含敏感原文和招聘方内部原因");
        ai.setAiCallLogId(83L);
        when(aiFeignClient.generateApplicationEventReview(any())).thenReturn(Result.success(ai));

        JobApplicationEventStructuredReviewVO result =
                service.generate(88L, 908L, new JobApplicationEventReviewGenerateDTO());

        assertEquals("RULE", result.getAnalysis().getOwner());
        assertEquals("FALLBACK", result.getGeneration().getStatus());
        assertTrue(result.getGeneration().getFallback());
        assertFalse(storedJson.get().contains("user@example.com"));
        assertFalse(storedJson.get().contains("13812341234"));
        assertFalse(storedJson.get().contains("招聘方认为"));
        assertFalse(storedJson.get().contains("自动为你投递"));
        assertFalse(storedJson.get().contains("rawResponse"));
    }

    @Test
    void malformedAiRawResponseIsNotEvidenceOrUserPayload() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(
                909L,
                88L,
                "INTERVIEW_COMPLETED",
                "{\"rawResponse\":\"历史模型原文\",\"nested\":{\"raw_response\":\"内部原文\"}}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(909L)).thenReturn(event);
        AtomicReference<String> storedJson = stubReviewJsonCas(event);

        GenerateApplicationEventReviewAiVO ai = new GenerateApplicationEventReviewAiVO();
        ai.setRawResponse("{not-json 招聘方认为你会获得 offer");
        ai.setAiCallLogId(84L);
        when(aiFeignClient.generateApplicationEventReview(any())).thenReturn(Result.success(ai));

        JobApplicationEventStructuredReviewVO result =
                service.generate(88L, 909L, new JobApplicationEventReviewGenerateDTO());

        assertEquals("RULE", result.getAnalysis().getOwner());
        assertEquals("FALLBACK", result.getGeneration().getStatus());
        assertTrue(result.getGeneration().getFallback());
        assertEquals(null, result.getGeneration().getAiCallLogId());
        assertFalse(storedJson.get().contains("rawResponse"));
        assertFalse(storedJson.get().contains("raw_response"));
        assertFalse(storedJson.get().contains("not-json"));
        assertFalse(storedJson.get().contains("历史模型原文"));
    }

    @Test
    void malformedAiJsonExceptionFallsBackWithoutPersistingProviderText() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(912L, 88L, "INTERVIEW_COMPLETED", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(912L)).thenReturn(event);
        AtomicReference<String> storedJson = stubReviewJsonCas(event);
        when(aiFeignClient.generateApplicationEventReview(any()))
                .thenThrow(new IllegalStateException(
                        "json parse failed: 招聘方认为候选人已通过面试"));

        JobApplicationEventStructuredReviewVO result =
                service.generate(88L, 912L, new JobApplicationEventReviewGenerateDTO());

        assertEquals("RULE", result.getAnalysis().getOwner());
        assertEquals("FALLBACK", result.getGeneration().getStatus());
        assertEquals("AI_PARSE_ERROR", result.getGeneration().getFallbackReason());
        assertFalse(storedJson.get().contains("json parse failed"));
        assertFalse(storedJson.get().contains("已通过面试"));
    }

    @Test
    void generatesStructuredReviewPreservesTopLevelEvidenceAndReusesSameFingerprint() throws Exception {
        JobApplication application = application(88L, USER_ID);
        application.setCompanyName("Example Co");
        application.setJobTitle("Backend Engineer");
        JobApplicationEvent event = event(
                900L,
                88L,
                "INTERVIEW_COMPLETED",
                "{\"source\":\"interview-report\",\"reportId\":55,\"interviewId\":44}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(900L)).thenReturn(event);
        AtomicReference<String> storedJson = new AtomicReference<>(event.getReviewJson());
        when(eventMapper.compareAndSetReviewJson(
                eq(900L), eq(USER_ID), eq(88L), any(), anyString()))
                .thenAnswer(invocation -> {
                    String oldJson = invocation.getArgument(3);
                    String newJson = invocation.getArgument(4);
                    if (!java.util.Objects.equals(oldJson, storedJson.get())) {
                        return 0;
                    }
                    storedJson.set(newJson);
                    event.setReviewJson(newJson);
                    return 1;
                });

        GenerateApplicationEventReviewAiVO ai = new GenerateApplicationEventReviewAiVO();
        ai.setSummary("优先补强已经确认的异常链路表达缺口。");
        GenerateApplicationEventReviewAiVO.Signal signal =
                new GenerateApplicationEventReviewAiVO.Signal();
        signal.setContent("异常处理链路值得安排一次聚焦复练。");
        signal.setFactRefs(List.of("U1", "UNKNOWN"));
        signal.setConfidenceLevel("MEDIUM");
        ai.setSignals(List.of(signal));
        ai.setLimits(List.of("当前没有真实招聘方直接反馈。"));
        ai.setAdjustments(List.of("只复练一条缓存失效链路。"));
        ai.setNextActions(List.of("录制一次三分钟回答。"));
        ai.setAiCallLogId(77L);
        when(aiFeignClient.generateApplicationEventReview(any())).thenReturn(Result.success(ai));

        JobApplicationEventReviewGenerateDTO request = new JobApplicationEventReviewGenerateDTO();
        request.setObservedFacts(List.of("I omitted the compensation step."));
        request.setSelfReflection("I need a clearer exception-flow structure.");

        JobApplicationEventStructuredReviewVO first = service.generate(88L, 900L, request);
        JobApplicationEventStructuredReviewVO repeated = service.generate(88L, 900L, request);

        assertEquals("INTERVIEW_COMPLETED", first.getScenario());
        assertEquals("SIMULATION", first.getEventScope());
        assertEquals("AI", first.getAnalysis().getOwner());
        assertEquals("SUCCEEDED", first.getGeneration().getStatus());
        assertFalse(first.getGeneration().getFallback());
        assertEquals(77L, first.getGeneration().getAiCallLogId());
        assertEquals(List.of("U1"), first.getAnalysis().getSignals().get(0).getFactRefs());
        assertTrue(first.getSystemFacts().get(0).getContent().contains("系统记录的事件类型"));
        assertEquals(first.getGeneration().getGeneratedAt(), repeated.getGeneration().getGeneratedAt());
        assertEquals(55, objectMapper.readTree(storedJson.get()).path("reportId").asInt());
        assertEquals(44, objectMapper.readTree(storedJson.get()).path("interviewId").asInt());
        verify(aiFeignClient).generateApplicationEventReview(any());
    }

    @Test
    void fingerprintUsesExactNormalizedAiRequestAndLegacyHypotheses() throws Exception {
        JobApplication application = application(88L, USER_ID);
        application.setCompanyName("Example Co");
        application.setJobTitle("Backend Engineer");
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("assumptions", List.of(
                "先验证投递渠道",
                "{\"content\":\"结构化历史假设\"}",
                "{\"broken\""));
        legacy.put("facts", List.of(
                Map.of("content", "历史业务事实"),
                Map.of("rawResponse", "不应进入证据")));
        legacy.put("rawResponse", "历史模型原文");
        JobApplicationEvent event = event(
                910L,
                88L,
                "INTERVIEW_COMPLETED",
                objectMapper.writeValueAsString(legacy));
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(910L)).thenReturn(event);
        AtomicReference<String> storedJson = stubReviewJsonCas(event);
        when(aiFeignClient.generateApplicationEventReview(any()))
                .thenReturn(Result.success(validAi("第一次分析")), Result.success(validAi("第二次分析")));

        JobApplicationEventReviewGenerateDTO request = new JobApplicationEventReviewGenerateDTO();
        request.setObservedFacts(List.of("  我记录了接口追问。  "));
        request.setExternalFeedback("没有明确反馈");
        request.setSelfReflection("需要收敛表达顺序。");

        JobApplicationEventStructuredReviewVO first = service.generate(88L, 910L, request);

        Map<String, Object> changedRoot = codec.readRoot(storedJson.get());
        changedRoot.put("assumptions", List.of("改为验证岗位关键词"));
        String changedJson = codec.write(changedRoot);
        storedJson.set(changedJson);
        event.setReviewJson(changedJson);

        JobApplicationEventStructuredReviewVO second = service.generate(88L, 910L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<GenerateApplicationEventReviewAiDTO> requestCaptor =
                ArgumentCaptor.forClass(GenerateApplicationEventReviewAiDTO.class);
        verify(aiFeignClient, times(2)).generateApplicationEventReview(requestCaptor.capture());
        List<GenerateApplicationEventReviewAiDTO> aiRequests = requestCaptor.getAllValues();
        GenerateApplicationEventReviewAiDTO firstAiRequest = aiRequests.get(0);
        GenerateApplicationEventReviewAiDTO secondAiRequest = aiRequests.get(1);

        assertEquals(
                List.of("先验证投递渠道", "结构化历史假设", "历史业务事实"),
                firstAiRequest.getLegacyHypotheses());
        assertEquals(
                List.of("改为验证岗位关键词", "历史业务事实"),
                secondAiRequest.getLegacyHypotheses());
        assertEquals("我记录了接口追问。", firstAiRequest.getFacts().get(0).getContent());
        assertEquals(
                "USER_REPORTED_NO_EXTERNAL_FEEDBACK",
                firstAiRequest.getFacts().get(1).getSourceType());
        assertEquals(
                policy.inputFingerprint(firstAiRequest),
                first.getGeneration().getInputFingerprint());
        assertEquals(
                policy.inputFingerprint(secondAiRequest),
                second.getGeneration().getInputFingerprint());
        assertNotEquals(
                first.getGeneration().getInputFingerprint(),
                second.getGeneration().getInputFingerprint());

        GenerateApplicationEventReviewAiDTO changedNormalizedInput =
                objectMapper.convertValue(secondAiRequest, GenerateApplicationEventReviewAiDTO.class);
        changedNormalizedInput.setJobTitle("Platform Engineer");
        assertNotEquals(
                second.getGeneration().getInputFingerprint(),
                policy.inputFingerprint(changedNormalizedInput));
        assertFalse(storedJson.get().contains("rawResponse"));
        assertFalse(storedJson.get().contains("不应进入证据"));
    }

    @Test
    void negativeExternalFeedbackKeepsConfidenceLow() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(911L, 88L, "INTERVIEW_COMPLETED", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(911L)).thenReturn(event);
        stubReviewJsonCas(event);
        when(aiFeignClient.generateApplicationEventReview(any()))
                .thenReturn(Result.success(validAi("只复盘已记录的表达过程。")));

        JobApplicationEventReviewGenerateDTO request = new JobApplicationEventReviewGenerateDTO();
        request.setObservedFacts(List.of(
                "我记录了项目追问。",
                "我记录了异常链路回答。",
                "我记录了复盘动作。"));
        request.setExternalFeedback("没有明确反馈");

        JobApplicationEventStructuredReviewVO result = service.generate(88L, 911L, request);

        assertEquals("SUCCEEDED", result.getGeneration().getStatus());
        assertFalse(result.getGeneration().getFallback());
        assertEquals("LOW", result.getGeneration().getConfidenceLevel());
        assertEquals(
                "USER_REPORTED_NO_EXTERNAL_FEEDBACK",
                result.getUserInput().getExternalFeedback().getSourceType());
        assertTrue(result.getGeneration().getConfidenceBasis().stream()
                .anyMatch(item -> item.contains("不能作为强证据")));
    }

    @Test
    void persistsRuleFallbackWhenAiFailsAndKeepsUserInput() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(901L, 88L, "REJECTION_REVIEW", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(901L)).thenReturn(event);
        when(eventMapper.compareAndSetReviewJson(eq(901L), eq(USER_ID), eq(88L), any(), anyString()))
                .thenAnswer(invocation -> {
                    event.setReviewJson(invocation.getArgument(4));
                    return 1;
                });
        when(aiFeignClient.generateApplicationEventReview(any()))
                .thenThrow(new IllegalStateException("provider timeout"));

        JobApplicationEventReviewGenerateDTO request = new JobApplicationEventReviewGenerateDTO();
        request.setObservedFacts(List.of("A rejection email was received."));
        request.setExternalFeedback("The email did not include a reason.");

        JobApplicationEventStructuredReviewVO result = service.generate(88L, 901L, request);

        assertEquals("RULE", result.getAnalysis().getOwner());
        assertEquals("FALLBACK", result.getGeneration().getStatus());
        assertTrue(result.getGeneration().getFallback());
        assertEquals("AI_TIMEOUT", result.getGeneration().getFallbackReason());
        assertEquals("A rejection email was received.",
                result.getUserInput().getObservedFacts().get(0).getContent());
        assertEquals("LOW", result.getGeneration().getConfidenceLevel());
        assertTrue(result.getAnalysis().getSummary().contains("拒绝结果"));
    }

    @Test
    void forceRegenerationPreservesExistingUserInputAndCallsAiAgain() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(902L, 88L, "INTERVIEW_FEEDBACK_REVIEW", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(902L)).thenReturn(event);
        AtomicReference<String> storedJson = new AtomicReference<>(event.getReviewJson());
        when(eventMapper.compareAndSetReviewJson(
                eq(902L), eq(USER_ID), eq(88L), any(), anyString()))
                .thenAnswer(invocation -> {
                    String oldJson = invocation.getArgument(3);
                    String newJson = invocation.getArgument(4);
                    if (!java.util.Objects.equals(oldJson, storedJson.get())) {
                        return 0;
                    }
                    storedJson.set(newJson);
                    event.setReviewJson(newJson);
                    return 1;
                });
        GenerateApplicationEventReviewAiVO firstAi = validAi("第一次分析");
        GenerateApplicationEventReviewAiVO secondAi = validAi("重新生成后的分析");
        when(aiFeignClient.generateApplicationEventReview(any()))
                .thenReturn(Result.success(firstAi), Result.success(secondAi));

        JobApplicationEventReviewGenerateDTO firstRequest =
                new JobApplicationEventReviewGenerateDTO();
        firstRequest.setObservedFacts(List.of("我在项目追问中遗漏了补偿流程。"));
        service.generate(88L, 902L, firstRequest);

        JobApplicationEventReviewGenerateDTO forceRequest =
                new JobApplicationEventReviewGenerateDTO();
        forceRequest.setForce(true);
        forceRequest.setObservedFacts(List.of("这条输入不应覆盖已保存的用户事实。"));
        JobApplicationEventStructuredReviewVO regenerated =
                service.generate(88L, 902L, forceRequest);

        assertEquals("我在项目追问中遗漏了补偿流程。",
                regenerated.getUserInput().getObservedFacts().get(0).getContent());
        assertEquals("重新生成后的分析", regenerated.getAnalysis().getSummary());
        verify(aiFeignClient, times(2)).generateApplicationEventReview(any());
    }

    @Test
    void concurrentClaimReturnsGeneratingWinnerWithoutSecondAiCall() {
        JobApplication application = application(88L, USER_ID);
        JobApplicationEvent event = event(903L, 88L, "NO_RESPONSE_REVIEW", "{}");
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(903L)).thenReturn(event);
        when(eventMapper.compareAndSetReviewJson(
                eq(903L), eq(USER_ID), eq(88L), any(), anyString()))
                .thenAnswer(invocation -> {
                    event.setReviewJson(invocation.getArgument(4));
                    return 0;
                });

        JobApplicationEventStructuredReviewVO result =
                service.generate(88L, 903L, new JobApplicationEventReviewGenerateDTO());

        assertEquals("GENERATING", result.getGeneration().getStatus());
        verify(aiFeignClient, never()).generateApplicationEventReview(any());
    }

    @Test
    void capsPersistedReviewJsonAtFortyEightKilobytesWithoutDroppingTopLevelEvidence() throws Exception {
        JobApplication application = application(88L, USER_ID);
        String padding = "x".repeat(35_000);
        JobApplicationEvent event = event(
                904L,
                88L,
                "INTERVIEW_COMPLETED",
                objectMapper.writeValueAsString(java.util.Map.of(
                        "reportId", 99,
                        "padding", padding)));
        when(applicationMapper.selectById(88L)).thenReturn(application);
        when(eventMapper.selectById(904L)).thenReturn(event);
        AtomicReference<String> storedJson = new AtomicReference<>(event.getReviewJson());
        when(eventMapper.compareAndSetReviewJson(
                eq(904L), eq(USER_ID), eq(88L), any(), anyString()))
                .thenAnswer(invocation -> {
                    String oldJson = invocation.getArgument(3);
                    String newJson = invocation.getArgument(4);
                    if (!java.util.Objects.equals(oldJson, storedJson.get())) {
                        return 0;
                    }
                    storedJson.set(newJson);
                    event.setReviewJson(newJson);
                    return 1;
                });
        GenerateApplicationEventReviewAiVO ai = new GenerateApplicationEventReviewAiVO();
        ai.setSummary("长".repeat(2_000));
        ai.setLimits(List.of(
                "限制一".repeat(200),
                "限制二".repeat(200),
                "限制三".repeat(200),
                "限制四".repeat(200)));
        ai.setAdjustments(List.of(
                "调整一".repeat(200),
                "调整二".repeat(200),
                "调整三".repeat(200),
                "调整四".repeat(200)));
        ai.setNextActions(List.of(
                "动作一".repeat(200),
                "动作二".repeat(200),
                "动作三".repeat(200),
                "动作四".repeat(200)));
        when(aiFeignClient.generateApplicationEventReview(any())).thenReturn(Result.success(ai));

        service.generate(88L, 904L, new JobApplicationEventReviewGenerateDTO());

        assertTrue(storedJson.get().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 48 * 1024);
        assertEquals(99, objectMapper.readTree(storedJson.get()).path("reportId").asInt());
        assertEquals(padding, objectMapper.readTree(storedJson.get()).path("padding").asText());
    }

    private AtomicReference<String> stubReviewJsonCas(JobApplicationEvent event) {
        AtomicReference<String> storedJson = new AtomicReference<>(event.getReviewJson());
        when(eventMapper.compareAndSetReviewJson(
                eq(event.getId()),
                eq(USER_ID),
                eq(event.getApplicationId()),
                any(),
                anyString()))
                .thenAnswer(invocation -> {
                    String oldJson = invocation.getArgument(3);
                    String newJson = invocation.getArgument(4);
                    if (!java.util.Objects.equals(oldJson, storedJson.get())) {
                        return 0;
                    }
                    storedJson.set(newJson);
                    event.setReviewJson(newJson);
                    return 1;
                });
        return storedJson;
    }

    private GenerateApplicationEventReviewAiVO.Signal signal(
            String content,
            List<String> factRefs) {
        GenerateApplicationEventReviewAiVO.Signal signal =
                new GenerateApplicationEventReviewAiVO.Signal();
        signal.setContent(content);
        signal.setFactRefs(factRefs);
        signal.setConfidenceLevel("MEDIUM");
        return signal;
    }

    private GenerateApplicationEventReviewAiVO validAi(String summary) {
        GenerateApplicationEventReviewAiVO ai = new GenerateApplicationEventReviewAiVO();
        ai.setSummary(summary);
        ai.setLimits(List.of("当前缺少招聘方直接反馈。"));
        ai.setAdjustments(List.of("本轮只调整一个表达变量。"));
        ai.setNextActions(List.of("完成一次可验证复练。"));
        return ai;
    }

    private JobApplication application(Long id, Long userId) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setUserId(userId);
        application.setStatus("INTERVIEWING");
        application.setSource("MANUAL");
        application.setDeleted(0);
        application.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 10, 0));
        return application;
    }

    private JobApplicationEvent event(Long id, Long applicationId, String eventType, String reviewJson) {
        JobApplicationEvent event = new JobApplicationEvent();
        event.setId(id);
        event.setUserId(USER_ID);
        event.setApplicationId(applicationId);
        event.setEventType(eventType);
        event.setEventTime(LocalDateTime.of(2026, 7, 18, 11, 0));
        event.setSummary("Recorded application event");
        event.setReviewJson(reviewJson);
        event.setDeleted(0);
        event.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 11, 5));
        return event;
    }
}
