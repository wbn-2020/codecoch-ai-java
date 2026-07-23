package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewRemediation;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewStage;
import com.codecoachai.interview.domain.enums.InterviewModeEnum;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.enums.InterviewStatusEnum;
import com.codecoachai.interview.domain.vo.FinishInterviewVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.InterviewReportGenerateResultVO;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.domain.vo.StartInterviewVO;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.QuestionFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.EvaluateAnswerDTO;
import com.codecoachai.interview.feign.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.interview.feign.dto.InnerSelectQuestionDTO;
import com.codecoachai.interview.feign.vo.EvaluateAnswerVO;
import com.codecoachai.interview.feign.vo.GenerateInterviewQuestionVO;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationSummaryVO;
import com.codecoachai.interview.feign.vo.InnerQuestionVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeJobMatchReportVO;
import com.codecoachai.interview.feign.vo.InnerSkillGapItemVO;
import com.codecoachai.interview.feign.vo.InnerSkillProfileVO;
import com.codecoachai.interview.feign.vo.InnerTargetJobVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewRemediationMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewStageMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.codecoachai.interview.scenario.InterviewScenarioBindingMapper;
import com.codecoachai.interview.scenario.ScenarioBindingVO;
import com.codecoachai.interview.scenario.ScenarioRubricService;
import com.codecoachai.interview.scenario.ScenarioVersionVO;
import com.codecoachai.interview.service.IndustryTemplateService;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.service.InterviewVoiceService;
import com.codecoachai.interview.support.InterviewReportComparabilityPolicy;
import com.codecoachai.interview.voicedelivery.VoiceDeliverySummaryService;
import com.codecoachai.interview.voicedelivery.VoiceDeliverySummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewStageMapper stageMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewRemediationMapper remediationMapper;
    @Mock
    private QuestionFeignClient questionFeignClient;
    @Mock
    private ResumeFeignClient resumeFeignClient;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private InterviewReportAsyncService reportAsyncService;
    @Mock
    private IndustryTemplateService industryTemplateService;
    @Mock
    private InterviewMqDispatcher interviewMqDispatcher;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;
    @Mock
    private InterviewVoiceService interviewVoiceService;
    @Mock
    private VoiceDeliverySummaryService voiceDeliverySummaryService;
    @Mock
    private ScenarioRubricService scenarioRubricService;
    @Mock
    private InterviewScenarioBindingMapper scenarioBindingMapper;

    private InterviewService service;
    private InterviewServiceImpl target;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(InterviewSession.class);
        initTableInfo(InterviewStage.class);
        initTableInfo(InterviewMessage.class);
        initTableInfo(InterviewReport.class);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    @BeforeEach
    void setUp() {
        target = new InterviewServiceImpl(
                sessionMapper,
                stageMapper,
                messageMapper,
                reportMapper,
                remediationMapper,
                questionFeignClient,
                resumeFeignClient,
                aiFeignClient,
                reportAsyncService,
                industryTemplateService,
                interviewMqDispatcher,
                agentBusinessActionNotifier,
                interviewVoiceService,
                voiceDeliverySummaryService,
                scenarioRubricService,
                scenarioBindingMapper,
                new ObjectMapper(),
                new TransactionTemplate(new FlaggingTransactionManager()),
                new InterviewReportComparabilityPolicy(new ObjectMapper()));
        service = transactionalProxy(target);
        lenient().when(sessionMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        lenient().when(reportMapper.update(any(InterviewReport.class), any(Wrapper.class))).thenReturn(1);
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .username("tester")
                .roles(List.of("USER"))
                .build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createPersistsApplicationIdAfterOwnershipValidation() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setApplicationId(501L);
        dto.setInterviewMode(InterviewModeEnum.COMPREHENSIVE.name());
        dto.setMaxQuestionCount(3);
        InnerJobApplicationSummaryVO application = applicationSummary(501L, 10L);
        when(resumeFeignClient.getApplicationSummary(10L, 501L)).thenReturn(Result.success(application));
        when(resumeFeignClient.getSuccessResumeJobMatchReport(800L)).thenReturn(Result.success(matchReport()));
        when(resumeFeignClient.getTargetJob(10L, 300L)).thenReturn(Result.success(targetJob()));
        InnerResumeDetailVO resume = new InnerResumeDetailVO();
        resume.setId(100L);
        resume.setUserId(10L);
        when(resumeFeignClient.getResume(100L)).thenReturn(Result.success(resume));
        when(sessionMapper.insert(any(InterviewSession.class))).thenAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            session.setId(1L);
            return 1;
        });

        var result = service.create(dto);

        assertEquals(501L, result.getApplicationId());
        assertEquals(300L, result.getTargetJobId());
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        assertEquals(501L, sessionCaptor.getValue().getApplicationId());
    }

    @Test
    void createBindsPublishedScenarioAndMaterializesItsStages() throws Exception {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setInterviewMode(InterviewModeEnum.COMPREHENSIVE.name());
        dto.setScenarioVersionId(71L);

        ScenarioVersionVO scenario = new ScenarioVersionVO();
        scenario.setScenarioVersionId(71L);
        scenario.setRubricVersionId(81L);
        scenario.setScenarioCode("SYSTEM_DESIGN");
        scenario.setVersionStatus("PUBLISHED");
        scenario.setScript(new ObjectMapper().readTree("""
                {"questionBudget":3,"stages":[
                  {"code":"REQUIREMENTS","name":"需求澄清","questionCount":1,"allowFollowUp":false},
                  {"code":"ARCHITECTURE","name":"总体架构","questionCount":2,"focusPoints":["边界","取舍"]}
                ]}
                """));
        when(resumeFeignClient.getCurrentTargetJob(10L)).thenReturn(Result.success(null));
        when(scenarioRubricService.getPublishedScenarioVersion(71L)).thenReturn(scenario);
        when(sessionMapper.insert(any(InterviewSession.class))).thenAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            session.setId(1L);
            return 1;
        });
        ScenarioBindingVO binding = new ScenarioBindingVO();
        binding.setSessionId(1L);
        binding.setScenarioVersionId(71L);
        binding.setRubricVersionId(81L);
        when(scenarioRubricService.bindScenario(eq(1L), any())).thenReturn(binding);

        var result = service.create(dto);

        assertEquals(71L, result.getScenarioVersionId());
        assertEquals(81L, result.getRubricVersionId());
        assertEquals("SYSTEM_DESIGN", result.getScenarioCode());
        assertEquals(3, result.getTotalQuestionCount());
        ArgumentCaptor<InterviewStage> stageCaptor = ArgumentCaptor.forClass(InterviewStage.class);
        verify(stageMapper, org.mockito.Mockito.times(2)).insert(stageCaptor.capture());
        assertEquals(List.of("REQUIREMENTS", "ARCHITECTURE"),
                stageCaptor.getAllValues().stream().map(InterviewStage::getStageType).toList());
        assertFalse(stageCaptor.getAllValues().get(0).getAllowFollowUp());
    }

    @Test
    void createRejectsApplicationIdWhenOwnershipCannotBeValidated() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setApplicationId(501L);
        when(resumeFeignClient.getApplicationSummary(10L, 501L)).thenThrow(new RuntimeException("resume unavailable"));

        assertThrows(BusinessException.class, () -> service.create(dto));

        verify(sessionMapper, never()).insert(any(InterviewSession.class));
    }

    @Test
    void startDoesNotHoldTransactionWhileCallingRemoteQuestionGeneration() {
        InterviewSession session = session();
        InterviewStage stage = stage();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(stageMapper.selectOne(any())).thenReturn(stage);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(questionFeignClient.select(any(InnerSelectQuestionDTO.class))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "question selection must not run inside interview DB transaction");
            return Result.success(question());
        });
        when(aiFeignClient.generateQuestion(any(GenerateInterviewQuestionDTO.class))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "AI question generation must not run inside interview DB transaction");
            return Result.success(aiQuestion());
        });
        when(messageMapper.insert(any(InterviewMessage.class))).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "generated question persistence must run inside a short DB transaction");
            InterviewMessage message = invocation.getArgument(0);
            message.setId(1001L);
            return 1;
        });

        StartInterviewVO result = service.start(1L);

        assertNotNull(result.getCurrentQuestion());
    }

    @Test
    void startAcceptsAiGeneratedFallbackQuestionWithoutPersistedQuestionId() {
        InterviewSession session = session();
        InterviewStage stage = stage();
        InnerQuestionVO fallback = question();
        fallback.setId(null);
        fallback.setGroupId(null);
        fallback.setTitle("AI_GENERATED_FALLBACK");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(stageMapper.selectOne(any())).thenReturn(stage);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(questionFeignClient.select(any(InnerSelectQuestionDTO.class))).thenReturn(Result.success(fallback));
        when(aiFeignClient.generateQuestion(any(GenerateInterviewQuestionDTO.class))).thenReturn(Result.success(aiQuestion()));
        when(messageMapper.insert(any(InterviewMessage.class))).thenAnswer(invocation -> {
            InterviewMessage message = invocation.getArgument(0);
            assertEquals(null, message.getQuestionId());
            assertNotNull(message.getQuestionContent());
            message.setId(1002L);
            return 1;
        });

        StartInterviewVO result = service.start(1L);

        assertNotNull(result.getCurrentQuestion());
    }

    @Test
    void answerDoesNotHoldTransactionWhileCallingAiEvaluation() {
        InterviewSession session = waitingSession();
        InterviewStage stage = stage();
        InterviewMessage currentQuestion = currentQuestionMessage();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(stageMapper.selectById(11L)).thenReturn(stage);
        when(messageMapper.selectOne(any())).thenReturn(currentQuestion);
        when(questionFeignClient.getQuestion(101L)).thenReturn(Result.success(question()));
        when(messageMapper.selectList(any())).thenReturn(List.of(currentQuestion));
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(sessionMapper.update(any(), any())).thenReturn(1);
        when(questionFeignClient.select(any(InnerSelectQuestionDTO.class))).thenReturn(Result.success(question()));
        when(aiFeignClient.generateQuestion(any(GenerateInterviewQuestionDTO.class))).thenReturn(Result.success(aiQuestion()));
        when(messageMapper.insert(any(InterviewMessage.class))).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "answer and evaluation persistence must run inside short DB transactions");
            InterviewMessage message = invocation.getArgument(0);
            message.setId("ANSWER".equals(message.getMessageType()) ? 2001L : 2002L);
            return 1;
        });
        when(aiFeignClient.evaluate(any(EvaluateAnswerDTO.class))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "AI answer evaluation must not run inside interview DB transaction");
            return Result.success(evaluation());
        });

        SubmitInterviewAnswerDTO dto = new SubmitInterviewAnswerDTO();
        dto.setQuestionId(101L);
        dto.setAnswerContent("HashMap uses buckets and resizes by threshold.");
        dto.setNeedFollowUp(false);

        assertNotNull(service.answer(1L, dto).getEvaluationMessageId());
    }

    @Test
    void answerForSseConsumesAiTokenStreamBeforePersistingEvaluation() {
        InterviewSession session = waitingSession();
        InterviewStage stage = stage();
        InterviewMessage currentQuestion = currentQuestionMessage();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(stageMapper.selectById(11L)).thenReturn(stage);
        when(messageMapper.selectOne(any())).thenReturn(currentQuestion);
        when(questionFeignClient.getQuestion(101L)).thenReturn(Result.success(question()));
        when(messageMapper.selectList(any())).thenReturn(List.of(currentQuestion));
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(sessionMapper.update(any(), any())).thenReturn(1);
        when(questionFeignClient.select(any(InnerSelectQuestionDTO.class))).thenReturn(Result.success(question()));
        when(aiFeignClient.generateQuestion(any(GenerateInterviewQuestionDTO.class))).thenReturn(Result.success(aiQuestion()));
        when(messageMapper.insert(any(InterviewMessage.class))).thenAnswer(invocation -> {
            InterviewMessage message = invocation.getArgument(0);
            message.setId("ANSWER".equals(message.getMessageType()) ? 2001L : 2002L);
            return 1;
        });
        when(aiFeignClient.evaluateStream(any(EvaluateAnswerDTO.class))).thenReturn(streamResponse("""
                event: token
                data: {"type":"token","content":"Good "}

                event: token
                data: {"type":"token","content":"answer"}

                event: result
                data: {"type":"result","result":{"aiCallLogId":301,"score":80,"comment":"Good answer","nextAction":"FINISH","knowledgePoints":"HashMap"}}

                event: done
                data: {"type":"done"}

                """));

        SubmitInterviewAnswerDTO dto = new SubmitInterviewAnswerDTO();
        dto.setQuestionId(101L);
        dto.setAnswerContent("HashMap uses buckets and resizes by threshold.");
        dto.setNeedFollowUp(false);
        List<String> tokens = new ArrayList<>();

        assertNotNull(service.answerForSse(1L, dto, stageName -> {
        }, tokens::add).getEvaluationMessageId());
        assertTrue(tokens.containsAll(List.of("Good ", "answer")), "SSE answer review should forward AI tokens");
    }

    @Test
    void reportIncludesTargetJobAndJdGapContextFromSession() throws Exception {
        InterviewSession session = completedJdSession();
        InterviewReport report = generatedReportForSession();
        InnerSkillProfileVO profile = skillProfileWithJdGap();

        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        when(resumeFeignClient.getSkillProfile(700L)).thenReturn(Result.success(profile));
        VoiceDeliverySummaryVO delivery = new VoiceDeliverySummaryVO();
        delivery.setSessionId(1L);
        delivery.setAvailable(Boolean.TRUE);
        delivery.setStatus("SUCCEEDED");
        delivery.setSpeakingRatePerMinute(new java.math.BigDecimal("155.00"));
        when(voiceDeliverySummaryService.summary(10L, 1L)).thenReturn(delivery);

        InterviewReportVO result = service.report(1L);

        assertEquals(501L, result.getApplicationId());
        assertEquals(300L, result.getTargetJobId());
        assertEquals(700L, result.getSkillProfileId());
        assertEquals(800L, result.getMatchReportId());
        assertEquals("Java Backend Engineer", readProperty(result, "getTargetJobTitle"));
        assertEquals("Acme", readProperty(result, "getTargetCompanyName"));
        assertEquals("Skill profile #700, match report #800", readProperty(result, "getJdEvidenceSummary"));
        List<?> missingSkills = readListProperty(result, "getMissingSkills");
        assertEquals(1, missingSkills.size());
        Object gap = missingSkills.get(0);
        assertEquals("Redis cache consistency", readProperty(gap, "getSkillName"));
        assertEquals("HIGH", readProperty(gap, "getSeverity"));
        assertEquals("JD expects high-concurrency cache consistency evidence", readProperty(gap, "getGapDescription"));
        assertEquals(List.of("Practice one cache consistency follow-up"), readListProperty(gap, "getRecommendedActions"));
        assertEquals("SUCCEEDED", readProperty(readProperty(result, "getVoiceDeliverySummary"), "getStatus"));
        assertEquals(new java.math.BigDecimal("155.00"),
                readProperty(readProperty(result, "getVoiceDeliverySummary"), "getSpeakingRatePerMinute"));
    }

    @Test
    void missingOwnedSessionUsesResourceNotFoundContract() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.detail(999L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void reportThatHasNotBeenGeneratedUsesSemanticValidationContract() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(10L);
        session.setReportStatus(ReportStatusEnum.NOT_GENERATED.name());
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.report(1L));

        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
    }

    @Test
    void listAttachesCurrentUserVoiceDeliverySummariesToHistoryRows() {
        InterviewSession first = completedJdSession();
        first.setId(1L);
        InterviewSession second = completedJdSession();
        second.setId(2L);
        Page<InterviewSession> page = Page.of(1, 10);
        page.setRecords(List.of(first, second));
        page.setTotal(2);
        when(sessionMapper.selectPage(any(), any())).thenReturn(page);

        VoiceDeliverySummaryVO summary = new VoiceDeliverySummaryVO();
        summary.setSessionId(1L);
        summary.setAnalysisId(91L);
        summary.setAvailable(Boolean.TRUE);
        summary.setStatus("SUCCEEDED");
        when(voiceDeliverySummaryService.summaries(10L, List.of(1L, 2L)))
                .thenReturn(Map.of(1L, summary));

        var result = service.list(1L, 10L, null, null, null);
        List<InterviewListVO> records = result.getRecords();

        assertEquals(2, records.size());
        assertEquals(91L, records.get(0).getVoiceDeliverySummary().getAnalysisId());
        assertNull(records.get(1).getVoiceDeliverySummary());
        verify(voiceDeliverySummaryService).summaries(10L, List.of(1L, 2L));
    }

    @Test
    void listUsesCurrentOwnedReportsForScoreAndComparabilityInOneBatch() throws Exception {
        InterviewSession first = completedJdSession();
        first.setId(1L);
        first.setTotalScore(99);
        InterviewSession second = completedJdSession();
        second.setId(2L);
        second.setTotalScore(42);
        Page<InterviewSession> page = Page.of(1, 10);
        page.setRecords(List.of(first, second));
        page.setTotal(2);
        when(sessionMapper.selectPage(any(), any())).thenReturn(page);
        when(voiceDeliverySummaryService.summaries(10L, List.of(1L, 2L))).thenReturn(Map.of());
        InterviewReport comparable = comparableReport();
        comparable.setTotalScore(65);
        InterviewReport older = comparableReport();
        older.setId(87L);
        InterviewReport missingTotal = comparableReport();
        missingTotal.setId(89L);
        missingTotal.setSessionId(2L);
        missingTotal.setTotalScore(null);
        lenient().when(reportMapper.selectList(any())).thenReturn(List.of(comparable, older, missingTotal));

        List<InterviewListVO> records = service.list(1L, 10L, null, null, null).getRecords();

        assertEquals(88L, readProperty(records.get(0), "getReportId"));
        assertEquals(65, records.get(0).getTotalScore());
        assertEquals(true, readProperty(records.get(0), "getComparisonAvailable"));
        assertNull(readProperty(records.get(0), "getComparisonUnavailableReason"));
        assertEquals(89L, readProperty(records.get(1), "getReportId"));
        assertEquals(42, records.get(1).getTotalScore());
        assertEquals(true, readProperty(records.get(1), "getComparisonAvailable"));
        assertNull(readProperty(records.get(1), "getComparisonUnavailableReason"));
        assertEquals("REPORT_RUBRIC_SCORES",
                readProperty(records.get(1), "getComparisonNormalizationSource"));
        assertTrue(records.get(1).getComparisonWarnings().stream().anyMatch(warning ->
                "TOTAL_SCORE_RECOVERED_FROM_SESSION".equals(warning.getCode())));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<InterviewReport>> reportQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper).selectList(reportQuery.capture());
        String sql = reportQuery.getValue().getSqlSegment().toLowerCase();
        List<Object> values = queryValues(reportQuery.getValue());
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("session_id") && sql.contains(" in "));
        assertTrue(sql.contains("deleted"));
        assertTrue(sql.contains("order by") && sql.contains("id") && sql.contains("desc"));
        assertTrue(values.containsAll(List.of(10L, 1L, 2L, 0)));
    }

    @Test
    void reportComparisonAvailabilityRejectsMissingTotalScore() throws Exception {
        InterviewReport report = comparableReport();
        report.setTotalScore(null);

        InterviewReportVO result = invokeToReportVO(report, completedJdSession());

        assertFalse(result.getComparisonAvailable());
        assertEquals("TOTAL_SCORE_MISSING", result.getComparisonUnavailableReason());
    }

    @Test
    void reportComparisonAvailabilityRejectsEmptyOrMalformedRubricData() throws Exception {
        InterviewReport empty = comparableReport();
        empty.setRubricScores("[]");
        InterviewReport malformed = comparableReport();
        malformed.setRubricScores("{not-json");

        InterviewReportVO emptyResult = invokeToReportVO(empty, completedJdSession());
        InterviewReportVO malformedResult = invokeToReportVO(malformed, completedJdSession());

        assertFalse(emptyResult.getComparisonAvailable());
        assertEquals("RUBRIC_DATA_MISSING", emptyResult.getComparisonUnavailableReason());
        assertFalse(malformedResult.getComparisonAvailable());
        assertEquals("RUBRIC_DATA_MALFORMED", malformedResult.getComparisonUnavailableReason());
    }

    @Test
    void reportComparisonAvailabilityAcceptsCompleteComparableReport() throws Exception {
        InterviewReportVO result = invokeToReportVO(comparableReport(), completedJdSession());

        assertTrue(result.getComparisonAvailable());
        assertNull(result.getComparisonUnavailableReason());
    }

    @Test
    void reportMarksCreatedRemediationAndLinksNextInterviewAction() {
        InterviewSession session = completedJdSession();
        InterviewReport report = generatedReportForSession();
        InterviewRemediation remediation = new InterviewRemediation();
        remediation.setId(500L);
        remediation.setSourceReportId(88L);
        remediation.setTargetSessionId(200L);
        remediation.setStatus("CREATED");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        when(remediationMapper.selectOne(any())).thenReturn(remediation);

        InterviewReportVO result = service.report(1L);

        assertEquals(true, result.getRemediationCreated());
        assertEquals(500L, result.getRemediationId());
        assertEquals(200L, result.getRemediationTargetSessionId());
        assertTrue(result.getNextActions().stream().anyMatch(action ->
                "INTERVIEW".equals(action.getActionType())
                        && "复练已创建".equals(action.getTitle())
                        && "/interviews/room/200".equals(action.getActionUrl())));
    }

    @Test
    void generateReportForSseCompletesAgentInterviewTaskWithGeneratedReportEvidence() {
        InterviewSession session = completedTargetJobSession();
        AtomicReference<InterviewReport> latest = new AtomicReference<>();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenAnswer(invocation -> latest.get());
        when(reportMapper.insert(any(InterviewReport.class))).thenAnswer(invocation -> {
            InterviewReport report = invocation.getArgument(0);
            report.setId(88L);
            latest.set(report);
            return 1;
        });
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        when(aiFeignClient.report(any())).thenReturn(Result.success(generatedAiReport()));
        when(resumeFeignClient.getTargetJob(10L, 300L)).thenReturn(Result.success(null));

        service.generateReportForSse(1L, null, false, stage -> {
        });

        verify(agentBusinessActionNotifier).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void generateReportForSseRejectsMissingRubricInsteadOfInventingDefaultDimensions() {
        InterviewSession session = completedTargetJobSession();
        AtomicReference<InterviewReport> latest = new AtomicReference<>();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenAnswer(invocation -> latest.get());
        when(reportMapper.insert(any(InterviewReport.class))).thenAnswer(invocation -> {
            InterviewReport report = invocation.getArgument(0);
            report.setId(88L);
            latest.set(report);
            return 1;
        });
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        GenerateReportVO incomplete = generatedAiReport();
        incomplete.setRubricScores(null);
        when(aiFeignClient.report(any())).thenReturn(Result.success(incomplete));
        when(resumeFeignClient.getTargetJob(10L, 300L)).thenReturn(Result.success(null));

        InterviewReportGenerateResultVO result =
                service.generateReportForSse(1L, null, false, stage -> {
                });

        assertEquals(ReportStatusEnum.FAILED.name(), result.getResult().getStatus());
        assertNull(result.getResult().getTotalScore());
        assertTrue(result.getResult().getRubricScores().isEmpty());
        assertNull(result.getResult().getRubricVersion());
        assertFalse(result.getResult().getComparisonAvailable());
        assertEquals("REPORT_NOT_GENERATED", result.getResult().getComparisonUnavailableReason());
        assertTrue(result.getResult().getFailureReason().contains("RUBRIC_DATA_MISSING"));
        verify(agentBusinessActionNotifier, never()).completeInterviewReport(anyLong(), anyLong(), anyLong());
    }

    @Test
    void finishReusesGeneratingReportWithoutDispatchingDuplicateTask() {
        InterviewSession session = waitingSession();
        session.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        session.setReportStatus(ReportStatusEnum.GENERATING.name());
        InterviewReport generating = new InterviewReport();
        generating.setId(77L);
        generating.setSessionId(1L);
        generating.setUserId(10L);
        generating.setStatus(ReportStatusEnum.GENERATING.name());
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        when(reportMapper.selectOne(any())).thenReturn(generating);

        FinishInterviewVO result = service.finish(1L);

        assertNotNull(result.getReport());
        assertEquals(77L, result.getReport().getId());
        verify(interviewMqDispatcher, never()).dispatchReportWithReceipt(anyLong(), anyLong(), anyLong(), any());
        verify(reportAsyncService, never()).generateReportAsync(anyLong(), anyLong(), any());
    }

    @Test
    void generateReportForSseReturnsExistingGeneratingReportWithoutRegeneration() {
        InterviewSession session = completedTargetJobSession();
        session.setReportStatus(ReportStatusEnum.GENERATING.name());
        InterviewReport existing = generatedReportForSession();
        existing.setStatus(ReportStatusEnum.GENERATING.name());
        existing.setGenerationToken("token-generating");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        InterviewReportGenerateResultVO result = service.generateReportForSse(1L, 88L, false, stage -> {
        });

        assertEquals(88L, result.getReportId());
        verify(aiFeignClient, never()).report(any());
        verify(reportMapper, never()).insert(any(InterviewReport.class));
    }

    @Test
    void generateReportForSseForceRegenerateReusesExistingReportRow() {
        InterviewSession session = completedTargetJobSession();
        InterviewReport existing = generatedReportForSession();
        existing.setGenerationToken("token-old");
        AtomicReference<InterviewReport> latest = new AtomicReference<>(existing);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenAnswer(invocation -> latest.get());
        when(reportMapper.update(any(InterviewReport.class), any(Wrapper.class))).thenAnswer(invocation -> {
            latest.set(invocation.getArgument(0));
            return 1;
        });
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        when(aiFeignClient.report(any())).thenReturn(Result.success(generatedAiReport()));
        when(resumeFeignClient.getTargetJob(10L, 300L)).thenReturn(Result.success(null));

        InterviewReportGenerateResultVO result = service.generateReportForSse(1L, 88L, true, stage -> {
        });

        assertEquals(88L, result.getReportId());
        verify(reportMapper, never()).insert(any(InterviewReport.class));
        verify(reportMapper, org.mockito.Mockito.atLeastOnce()).update(
                org.mockito.ArgumentMatchers.<InterviewReport>argThat(report -> report.getId().equals(88L)
                        && ReportStatusEnum.GENERATED.name().equals(report.getStatus())),
                any(Wrapper.class));
    }

    @Test
    void generateReportForSseSyncsGeneratedReportToApplicationEventWhenSessionIsBound() {
        InterviewSession session = completedTargetJobSession();
        session.setApplicationId(501L);
        AtomicReference<InterviewReport> latest = new AtomicReference<>();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenAnswer(invocation -> latest.get());
        when(reportMapper.insert(any(InterviewReport.class))).thenAnswer(invocation -> {
            InterviewReport report = invocation.getArgument(0);
            report.setId(88L);
            latest.set(report);
            return 1;
        });
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        when(aiFeignClient.report(any())).thenReturn(Result.success(generatedAiReport()));
        when(resumeFeignClient.getTargetJob(10L, 300L)).thenReturn(Result.success(null));
        when(resumeFeignClient.createApplicationEvent(eq(10L), eq(501L), any())).thenReturn(Result.success());

        service.generateReportForSse(1L, null, false, stage -> {
        });

        verify(resumeFeignClient).createApplicationEvent(eq(10L), eq(501L), argThat(event ->
                "INTERVIEW_COMPLETED".equals(event.getEventType())
                        && event.getReview() != null
                        && Long.valueOf(1L).equals(event.getReview().get("interviewId"))
                        && Long.valueOf(88L).equals(event.getReview().get("reportId"))));
    }

    @Test
    void finishDispatchesReportGenerationWithStableGenerationToken() {
        InterviewSession session = waitingSession();
        session.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        session.setReportStatus(ReportStatusEnum.NOT_GENERATED.name());
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(List.of(scorableAnswer()));
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(reportMapper.insert(any(InterviewReport.class))).thenAnswer(invocation -> {
            InterviewReport report = invocation.getArgument(0);
            report.setId(91L);
            return 1;
        });
        when(interviewMqDispatcher.dispatchReportWithReceipt(eq(1L), eq(10L), eq(91L),
                argThat(token -> token != null && !token.isBlank())))
                .thenReturn(mockDispatchReceipt("msg-1", "trace-1", "INTERVIEW_REPORT", "91", "SENT"));

        FinishInterviewVO result = service.finish(1L);

        assertNotNull(result.getReport());
        assertEquals(91L, result.getReport().getId());
        assertEquals("msg-1", result.getAsyncMessageId());
        assertEquals("trace-1", result.getAsyncTraceId());
        assertEquals("INTERVIEW_REPORT", result.getAsyncBizType());
        assertEquals("91", result.getAsyncBizId());
        assertEquals("SENT", result.getAsyncSendStatus());
        verify(interviewMqDispatcher).dispatchReportWithReceipt(eq(1L), eq(10L), eq(91L),
                argThat(token -> token != null && !token.isBlank()));
        verify(reportAsyncService, never()).generateReportAsync(anyLong(), anyLong(), any());
    }

    private InterviewService transactionalProxy(InterviewServiceImpl target) {
        MatchAlwaysTransactionAttributeSource source = new MatchAlwaysTransactionAttributeSource();
        source.setTransactionAttribute(new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRED));
        TransactionInterceptor interceptor = new TransactionInterceptor(new FlaggingTransactionManager(), source);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addInterface(InterviewService.class);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            Method method = invocation.getMethod();
            Method implementationMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            if (implementationMethod.isAnnotationPresent(Transactional.class)) {
                return interceptor.invoke(invocation);
            }
            return invocation.proceed();
        });
        return (InterviewService) proxyFactory.getProxy();
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(10L);
        session.setApplicationId(501L);
        session.setMode(InterviewModeEnum.COMPREHENSIVE.name());
        session.setStatus(InterviewStatusEnum.NOT_STARTED.name());
        session.setAnsweredQuestionCount(0);
        session.setMaxQuestionCount(3);
        session.setCurrentFollowUpCount(0);
        session.setDifficulty("MEDIUM");
        session.setExperienceLevel("MID");
        session.setCreatedAt(LocalDateTime.now());
        return session;
    }

    private InnerJobApplicationSummaryVO applicationSummary(Long id, Long userId) {
        InnerJobApplicationSummaryVO summary = new InnerJobApplicationSummaryVO();
        summary.setId(id);
        summary.setUserId(userId);
        summary.setTargetJobId(300L);
        summary.setMatchReportId(800L);
        summary.setResumeVersionId(200L);
        return summary;
    }

    private InnerTargetJobVO targetJob() {
        InnerTargetJobVO targetJob = new InnerTargetJobVO();
        targetJob.setId(300L);
        targetJob.setUserId(10L);
        targetJob.setJobTitle("Java Backend Engineer");
        return targetJob;
    }

    private InnerResumeJobMatchReportVO matchReport() {
        InnerResumeJobMatchReportVO report = new InnerResumeJobMatchReportVO();
        report.setReportId(800L);
        report.setUserId(10L);
        report.setResumeId(100L);
        report.setResumeVersionId(200L);
        report.setTargetJobId(300L);
        report.setJdAnalysisId(400L);
        report.setStatus("SUCCESS");
        return report;
    }

    private InterviewSession waitingSession() {
        InterviewSession session = session();
        session.setCurrentStageId(11L);
        session.setCurrentQuestionId(101L);
        session.setCurrentQuestionGroupId(201L);
        session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
        return session;
    }

    private InterviewStage stage() {
        InterviewStage stage = new InterviewStage();
        stage.setId(11L);
        stage.setSessionId(1L);
        stage.setStageType("JAVA_BASIC");
        stage.setStageName("Java 基础");
        stage.setSort(1);
        stage.setExpectedQuestionCount(1);
        stage.setAskedQuestionCount(0);
        stage.setStatus(InterviewStatusEnum.NOT_STARTED.name());
        return stage;
    }

    private InnerQuestionVO question() {
        InnerQuestionVO question = new InnerQuestionVO();
        question.setId(101L);
        question.setGroupId(201L);
        question.setTitle("HashMap");
        question.setContent("Explain HashMap internals.");
        question.setReferenceAnswer("hash, bucket, resize");
        return question;
    }

    private GenerateInterviewQuestionVO aiQuestion() {
        GenerateInterviewQuestionVO vo = new GenerateInterviewQuestionVO();
        vo.setQuestionContent("请说明 HashMap put 与 resize 的关键流程。");
        return vo;
    }

    private InterviewMessage currentQuestionMessage() {
        InterviewMessage message = new InterviewMessage();
        message.setId(100L);
        message.setSessionId(1L);
        message.setStageId(11L);
        message.setQuestionId(101L);
        message.setQuestionGroupId(201L);
        message.setRole("AI");
        message.setMessageType("QUESTION");
        message.setQuestionContent("Explain HashMap internals.");
        message.setContent("Explain HashMap internals.");
        message.setIsFollowUp(false);
        message.setFollowUpCount(0);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private EvaluateAnswerVO evaluation() {
        EvaluateAnswerVO vo = new EvaluateAnswerVO();
        vo.setAiCallLogId(301L);
        vo.setScore(80);
        vo.setComment("Good enough");
        vo.setNextAction("FINISH");
        vo.setKnowledgePoints("HashMap");
        return vo;
    }

    private MqDispatchReceipt mockDispatchReceipt(String messageId, String traceId, String bizType, String bizId,
                                                  String sendStatus) {
        return MqDispatchReceipt.builder()
                .messageId(messageId)
                .traceId(traceId)
                .bizType(bizType)
                .bizId(bizId)
                .sendStatus(sendStatus)
                .build();
    }

    private Response streamResponse(String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/inner/ai/interview/evaluate/stream",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(200)
                .reason("OK")
                .request(request)
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    private InterviewSession completedJdSession() {
        InterviewSession session = session();
        session.setTargetJobId(300L);
        session.setSkillProfileId(700L);
        session.setMatchReportId(800L);
        session.setTargetPosition("Java Backend Engineer");
        session.setStatus(InterviewStatusEnum.COMPLETED.name());
        session.setReportStatus(ReportStatusEnum.GENERATED.name());
        return session;
    }

    private InterviewSession completedTargetJobSession() {
        InterviewSession session = session();
        session.setApplicationId(null);
        session.setTargetJobId(300L);
        session.setTargetPosition("Java Backend Engineer");
        session.setStatus(InterviewStatusEnum.COMPLETED.name());
        session.setReportStatus(ReportStatusEnum.NOT_GENERATED.name());
        return session;
    }

    private InterviewReport generatedReportForSession() {
        InterviewReport report = new InterviewReport();
        report.setId(88L);
        report.setSessionId(1L);
        report.setUserId(10L);
        report.setStatus(ReportStatusEnum.GENERATED.name());
        report.setTotalScore(82);
        report.setSummary("Good report");
        report.setReportContent("Structured report");
        report.setQaReview("[{\"questionContent\":\"Redis?\",\"userAnswer\":\"Answer\"}]");
        report.setGeneratedAt(LocalDateTime.now());
        return report;
    }

    private InterviewReport comparableReport() {
        InterviewReport report = generatedReportForSession();
        report.setRubricVersion("INTERVIEW_RUBRIC_V1");
        report.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":4}]");
        return report;
    }

    private InterviewReportVO invokeToReportVO(InterviewReport report, InterviewSession session) throws Exception {
        Method method = InterviewServiceImpl.class.getDeclaredMethod(
                "toReportVO", InterviewReport.class, InterviewSession.class);
        method.setAccessible(true);
        return (InterviewReportVO) method.invoke(target, report, session);
    }

    private InterviewMessage scorableAnswer() {
        InterviewMessage message = new InterviewMessage();
        message.setId(2001L);
        message.setSessionId(1L);
        message.setRole("USER");
        message.setMessageType("ANSWER");
        message.setQuestionContent("How do you keep Redis and MySQL consistent?");
        message.setUserAnswer("Use delayed double delete and binlog compensation.");
        return message;
    }

    private GenerateReportVO generatedAiReport() {
        GenerateReportVO vo = new GenerateReportVO();
        vo.setAiCallLogId(301L);
        vo.setTotalScore(86);
        vo.setSummary("面试整体表现稳定。");
        vo.setReportContent("结构化面试报告内容");
        vo.setStrengths("[\"Java basics\"]");
        vo.setWeaknesses("[\"Redis consistency\"]");
        vo.setMainProblems("[\"Need deeper examples\"]");
        vo.setSuggestions("[\"Review cache consistency\"]");
        vo.setReviewSuggestions("[\"Practice one follow-up\"]");
        vo.setQaReview("[{\"questionContent\":\"How do you keep Redis and MySQL consistent?\",\"userAnswer\":\"Use delayed double delete\"}]");
        vo.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":4}]");
        vo.setAdviceEvidence("""
                [{"title":"Practice cache consistency","evidenceSources":[
                  {"sourceType":"INTERVIEW_REPORT","sourceId":88,"sourceSummary":"Report evidence"}
                ]}]
                """);
        vo.setAbilityProfileUpdates("[]");
        return vo;
    }

    private InnerSkillProfileVO skillProfileWithJdGap() {
        InnerSkillProfileVO profile = new InnerSkillProfileVO();
        profile.setProfileId(700L);
        profile.setUserId(10L);
        profile.setTargetJobId(300L);
        profile.setMatchReportId(800L);
        profile.setTargetJobTitle("Java Backend Engineer");
        profile.setTargetCompanyName("Acme");
        InnerSkillGapItemVO gap = new InnerSkillGapItemVO();
        gap.setId(900L);
        gap.setSkillName("Redis cache consistency");
        gap.setSeverity("HIGH");
        gap.setGapDescription("JD expects high-concurrency cache consistency evidence");
        gap.setRecommendedActionsJson("[\"Practice one cache consistency follow-up\"]");
        gap.setPriority(1);
        profile.setGapItems(List.of(gap));
        return profile;
    }

    private Object readProperty(Object target, String getterName) throws Exception {
        return target.getClass().getMethod(getterName).invoke(target);
    }

    private List<Object> queryValues(Object wrapper) {
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> query) {
            query.getSqlSegment();
            return new ArrayList<>(query.getParamNameValuePairs().values());
        }
        return List.of();
    }

    private List<?> readListProperty(Object target, String getterName) throws Exception {
        Object value = readProperty(target, getterName);
        assertTrue(value instanceof List<?>, getterName + " should return a list");
        return (List<?>) value;
    }

    private static final class FlaggingTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        @Override
        public void rollback(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
