package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.vo.InterviewReportAgentEvidenceVO;
import com.codecoachai.interview.domain.vo.InterviewWeaknessSummaryVO;
import com.codecoachai.interview.domain.vo.WeaknessInsightItemVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.codecoachai.interview.service.impl.AgentBusinessActionNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerInterviewReportControllerTest {

    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewMqDispatcher interviewMqDispatcher;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;

    private InnerInterviewReportController controller;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(InterviewSession.class);
        initTableInfo(InterviewMessage.class);
        initTableInfo(InterviewReport.class);
    }

    @BeforeEach
    void setUp() {
        controller = new InnerInterviewReportController(
                sessionMapper,
                messageMapper,
                reportMapper,
                interviewMqDispatcher,
                agentBusinessActionNotifier,
                new ObjectMapper());
    }

    @Test
    void getAgentEvidenceReturnsOwnedGeneratedReportTargetJob() {
        when(reportMapper.selectOne(any())).thenReturn(generatedReport());
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());

        Result<InterviewReportAgentEvidenceVO> result = controller.getAgentEvidence(10L, 88L);

        InterviewReportAgentEvidenceVO evidence = result.getData();
        assertNotNull(evidence);
        assertEquals(88L, evidence.getId());
        assertEquals(10L, evidence.getUserId());
        assertEquals(1L, evidence.getSessionId());
        assertEquals(300L, evidence.getTargetJobId());
        assertEquals(ReportStatusEnum.GENERATED.name(), evidence.getStatus());
    }

    @Test
    void completeReportCompletesAgentInterviewTaskWithReportEvidence() throws Exception {
        stubSessionCompletion();
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        InterviewReport current = generatedReport();
        current.setStatus(ReportStatusEnum.GENERATING.name());
        current.setGenerationToken("token-current");
        when(reportMapper.selectOne(any())).thenReturn(current);
        when(reportMapper.update(any(InterviewReport.class), any(Wrapper.class))).thenReturn(1);
        when(messageMapper.selectList(any())).thenReturn(answerOnlyMessages());
        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportId(88L);
        dto.setGenerationToken("token-current");
        dto.setReportStatus("SUCCESS");
        dto.setReportJson(new ObjectMapper().writeValueAsString(reportPayload()));
        dto.setTotalScore(82);

        controller.completeReport(1L, dto);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).update(reportCaptor.capture(), any(Wrapper.class));
        InterviewReport persisted = reportCaptor.getValue();
        assertEquals("ok", persisted.getSummary());
        assertEquals("[\"system design\"]", persisted.getWeakPoints());
        assertEquals("[\"clear communication\"]", persisted.getStrengths());
        assertEquals("[\"cache consistency\"]", persisted.getWeaknesses());
        assertEquals("[\"review by topic\"]", persisted.getReviewSuggestions());
        assertEquals("[\"keep practicing\"]", persisted.getSuggestions());
        assertEquals(completeQaReview(), persisted.getQaReview());
        assertEquals("[\"cache consistency\"]", persisted.getMainProblems());
        assertEquals(formalRubricScores(), persisted.getRubricScores());
        assertEquals("full report body", persisted.getReportContent());
        verify(agentBusinessActionNotifier).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void completeReportFailsWhenNeitherScoringContractNorAnswerEvidenceExists() {
        stubSessionCompletion();
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        InterviewReport current = generatedReport();
        current.setStatus(ReportStatusEnum.GENERATING.name());
        current.setGenerationToken("token-current");
        when(reportMapper.selectOne(any())).thenReturn(current);
        when(reportMapper.update(any(InterviewReport.class), any(Wrapper.class))).thenReturn(1);
        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportId(88L);
        dto.setGenerationToken("token-current");
        dto.setReportStatus("SUCCESS");
        dto.setReportJson("{\"summary\":\"ok\",\"reportContent\":\"full report body\"}");
        dto.setTotalScore(82);

        controller.completeReport(1L, dto);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).update(reportCaptor.capture(), any(Wrapper.class));
        InterviewReport persisted = reportCaptor.getValue();
        assertEquals(ReportStatusEnum.FAILED.name(), persisted.getStatus());
        assertEquals(null, persisted.getTotalScore());
        assertTrue(persisted.getFailureReason().contains("缺少有效回答证据"));
        verify(interviewMqDispatcher, never()).dispatchInterviewSearchUpsert(1L, 10L);
        verify(agentBusinessActionNotifier, never()).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void completeReportPreservesAnswersAsUnscorableWhenEvaluationEvidenceIsMissing() {
        stubSessionCompletion();
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        InterviewReport current = generatedReport();
        current.setStatus(ReportStatusEnum.GENERATING.name());
        current.setGenerationToken("token-current");
        when(reportMapper.selectOne(any())).thenReturn(current);
        when(reportMapper.update(any(InterviewReport.class), any(Wrapper.class))).thenReturn(1);
        when(messageMapper.selectList(any())).thenReturn(answerOnlyMessages());
        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportId(88L);
        dto.setGenerationToken("token-current");
        dto.setReportStatus("SUCCESS");
        dto.setReportJson("{\"summary\":\"ok\",\"reportContent\":\"full report body\"}");
        dto.setTotalScore(82);

        controller.completeReport(1L, dto);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).update(reportCaptor.capture(), any(Wrapper.class));
        InterviewReport persisted = reportCaptor.getValue();
        assertEquals(ReportStatusEnum.UNSCORABLE.name(), persisted.getStatus());
        assertEquals(null, persisted.getTotalScore());
        assertTrue(persisted.getQaReview().contains("回答一"));
        assertTrue(persisted.getQaReview().contains("STORED_INTERVIEW_MESSAGE"));
        assertTrue(persisted.getFailureReason().contains("最小可消费结构不完整"));
        verify(interviewMqDispatcher, never()).dispatchInterviewSearchUpsert(1L, 10L);
        verify(agentBusinessActionNotifier, never()).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void getReportContextPreservesRolesAnswersCommentsAndScores() {
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        when(messageMapper.selectList(any())).thenReturn(scoredMessages());

        Result<InnerInterviewReportController.ReportContextVO> result =
                controller.getReportContext(1L);

        List<String> messages = result.getData().getMessages();
        assertEquals(6, messages.size());
        assertTrue(messages.get(1).contains("Role:USER"));
        assertTrue(messages.get(1).contains("Type:ANSWER"));
        assertTrue(messages.get(1).contains("CandidateAnswer:回答一"));
        assertTrue(messages.get(2).contains("Role:AI"));
        assertTrue(messages.get(2).contains("Type:EVALUATION"));
        assertTrue(messages.get(2).contains("Score:85"));
        assertTrue(messages.get(2).contains("AiComment:点评一"));
    }

    @Test
    void completeReportKeepsRecoveredSingleDimensionEvidenceUnscorable() {
        stubSessionCompletion();
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        InterviewReport current = generatedReport();
        current.setStatus(ReportStatusEnum.GENERATING.name());
        current.setGenerationToken("token-current");
        when(reportMapper.selectOne(any())).thenReturn(current);
        when(reportMapper.update(any(InterviewReport.class), any(Wrapper.class))).thenReturn(1);
        when(messageMapper.selectList(any())).thenReturn(scoredMessages());

        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportId(88L);
        dto.setGenerationToken("token-current");
        dto.setReportStatus("SUCCESS");
        dto.setReportJson("""
                {"totalScore":null,"summary":null,"rubricScores":null,
                 "qaReview":null,"reportContent":null}
                """);

        controller.completeReport(1L, dto);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).update(reportCaptor.capture(), any(Wrapper.class));
        InterviewReport persisted = reportCaptor.getValue();
        assertEquals(ReportStatusEnum.UNSCORABLE.name(), persisted.getStatus());
        assertEquals(null, persisted.getTotalScore());
        assertTrue(persisted.getRubricScores().contains("ANSWER_QUALITY"));
        assertTrue(persisted.getRubricScores().contains("STORED_INTERVIEW_EVALUATION"));
        assertEquals(null, persisted.getRubricVersion());
        assertTrue(persisted.getQaReview().contains("回答一"));
        assertTrue(persisted.getQaReview().contains("点评二"));
        assertTrue(persisted.getSummary().contains("2 条有效回答"));
        assertTrue(persisted.getReportContent().contains("综合得分 84 分"));
        verify(interviewMqDispatcher, never()).dispatchInterviewSearchUpsert(1L, 10L);
        verify(agentBusinessActionNotifier, never()).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void completeReportSkipsSessionSideEffectsWhenPayloadTargetsStaleGenerationToken() {
        InterviewReport latest = generatedReport();
        latest.setStatus(ReportStatusEnum.GENERATING.name());
        latest.setGenerationToken("token-latest");
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        when(reportMapper.selectOne(any())).thenReturn(latest);
        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportId(88L);
        dto.setGenerationToken("token-old");
        dto.setReportStatus("SUCCESS");
        dto.setReportJson("{\"summary\":\"stale\"}");
        dto.setTotalScore(70);

        controller.completeReport(1L, dto);

        verify(sessionMapper, never()).update(any(), any());
        verify(reportMapper, never()).updateById(any(InterviewReport.class));
        verify(interviewMqDispatcher, never()).dispatchInterviewSearchUpsert(1L, 10L);
        verify(agentBusinessActionNotifier, never()).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void completeReportSkipsSessionSideEffectsWhenPayloadOmitsRequiredGenerationToken() {
        InterviewReport latest = generatedReport();
        latest.setStatus(ReportStatusEnum.GENERATING.name());
        latest.setGenerationToken("token-latest");
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        when(reportMapper.selectOne(any())).thenReturn(latest);
        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportId(88L);
        dto.setReportStatus("SUCCESS");
        dto.setReportJson("{\"summary\":\"stale\"}");
        dto.setTotalScore(70);

        controller.completeReport(1L, dto);

        verify(sessionMapper, never()).update(any(), any());
        verify(reportMapper, never()).updateById(any(InterviewReport.class));
        verify(interviewMqDispatcher, never()).dispatchInterviewSearchUpsert(1L, 10L);
        verify(agentBusinessActionNotifier, never()).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void completeReportTreatsDuplicateSuccessfulCallbackForSameAttemptAsIdempotentSuccess() {
        InterviewReport completed = generatedReport();
        completed.setGenerationToken("token-current");
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        when(reportMapper.selectOne(any())).thenReturn(completed);
        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportId(88L);
        dto.setGenerationToken("token-current");
        dto.setReportStatus("SUCCESS");

        Result<Void> result = controller.completeReport(1L, dto);

        assertEquals(0, result.getCode());
        verify(sessionMapper, never()).update(any(), any());
        verify(reportMapper, never()).update(any(InterviewReport.class), any(Wrapper.class));
        verify(interviewMqDispatcher, never()).dispatchInterviewSearchUpsert(1L, 10L);
        verify(agentBusinessActionNotifier, never()).completeInterviewReport(10L, 300L, 88L);
    }

    @Test
    void getSearchDocUsesLatestReportIdAsCurrentVersion() {
        AtomicReference<LambdaQueryWrapper<InterviewReport>> queryRef = new AtomicReference<>();
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        when(reportMapper.selectOne(any())).thenAnswer(invocation -> {
            queryRef.set(invocation.getArgument(0));
            return generatedReport();
        });

        controller.getSearchDoc(1L);

        LambdaQueryWrapper<InterviewReport> query = queryRef.get();
        assertNotNull(query);
        String sqlSegment = query.getSqlSegment();
        assertTrue(sqlSegment.contains("ORDER BY id DESC"),
                () -> "Current report should be selected by latest id, actual SQL: " + sqlSegment);
        assertTrue(!sqlSegment.contains("updated_at"),
                () -> "Current report query should not prefer updated_at over report id, actual SQL: " + sqlSegment);
    }

    @Test
    void weaknessSummaryReturnsEmptyTopWeaknessesWhenUserHasNoReports() {
        when(sessionMapper.selectCount(any())).thenReturn(2L);
        when(reportMapper.selectList(any())).thenReturn(List.of());

        Result<InterviewWeaknessSummaryVO> result = controller.weaknessSummary(10L, null);

        InterviewWeaknessSummaryVO summary = result.getData();
        assertNotNull(summary);
        assertEquals(30, summary.getRangeDays());
        assertEquals(2L, summary.getInterviewCount());
        assertEquals(0L, summary.getReportCount());
        assertTrue(summary.getTopWeaknesses().isEmpty());
    }

    @Test
    void weaknessSummaryUsesGeneratedAtWindowForReports() {
        when(sessionMapper.selectCount(any())).thenReturn(1L);
        when(reportMapper.selectList(any())).thenReturn(List.of(generatedReport()));

        controller.weaknessSummary(10L, 30);

        ArgumentCaptor<LambdaQueryWrapper<InterviewReport>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reportMapper).selectList(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sqlSegment.contains("generated_at"), sqlSegment);
        assertTrue(sqlSegment.contains("created_at"), sqlSegment);
        assertTrue(sqlSegment.indexOf("generated_at") < sqlSegment.indexOf("created_at"), sqlSegment);
    }

    @Test
    void weaknessSummaryExtractsWeaknessesFromStructuredReportFields() {
        when(sessionMapper.selectCount(any())).thenReturn(3L);
        InterviewReport report = generatedReport();
        report.setWeakPoints("[\"Redis 缓存一致性\", \"JVM 调优\"]");
        report.setMainProblems("[\"SQL 索引设计\"]");
        when(reportMapper.selectList(any())).thenReturn(List.of(report));

        Result<InterviewWeaknessSummaryVO> result = controller.weaknessSummary(10L, 30);

        List<WeaknessInsightItemVO> weaknesses = result.getData().getTopWeaknesses();
        assertEquals(3, weaknesses.size());
        assertEquals("Redis 缓存一致性", weaknesses.get(0).getName());
        assertEquals(1L, weaknesses.get(0).getCount());
        assertEquals("/weakness-analysis", weaknesses.get(0).getActionPath());
    }

    @Test
    void weaknessSummaryDeduplicatesSameWeaknessWithinOneReportBeforeCounting() {
        when(sessionMapper.selectCount(any())).thenReturn(2L);
        InterviewReport first = generatedReport();
        first.setWeakPoints("[\"Redis\", \" Redis \", \"SQL\"]");
        InterviewReport second = generatedReport();
        second.setId(89L);
        second.setWeaknesses("[\"Redis\"]");
        when(reportMapper.selectList(any())).thenReturn(List.of(first, second));

        Result<InterviewWeaknessSummaryVO> result = controller.weaknessSummary(10L, 30);

        List<WeaknessInsightItemVO> weaknesses = result.getData().getTopWeaknesses();
        assertEquals("Redis", weaknesses.get(0).getName());
        assertEquals(2L, weaknesses.get(0).getCount());
        assertEquals("SQL", weaknesses.get(1).getName());
        assertEquals(1L, weaknesses.get(1).getCount());
    }

    @Test
    void weaknessSummaryLimitsTopWeaknessesToFiveItems() {
        when(sessionMapper.selectCount(any())).thenReturn(1L);
        InterviewReport report = generatedReport();
        report.setWeakPoints("[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\"]");
        when(reportMapper.selectList(any())).thenReturn(List.of(report));

        Result<InterviewWeaknessSummaryVO> result = controller.weaknessSummary(10L, 90);

        assertEquals(90, result.getData().getRangeDays());
        assertEquals(5, result.getData().getTopWeaknesses().size());
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    private void stubSessionCompletion() {
        when(sessionMapper.update(any(), any(Wrapper.class))).thenReturn(1);
    }

    private InterviewSession targetJobSession() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(10L);
        session.setTargetJobId(300L);
        return session;
    }

    private InterviewReport generatedReport() {
        InterviewReport report = new InterviewReport();
        report.setId(88L);
        report.setSessionId(1L);
        report.setUserId(10L);
        report.setStatus(ReportStatusEnum.GENERATED.name());
        report.setTotalScore(82);
        report.setSummary("可信面试报告摘要");
        report.setReportContent("可信面试报告正文");
        report.setGeneratedAt(LocalDateTime.now().minusDays(1));
        return report;
    }

    private Map<String, Object> reportPayload() {
        return Map.ofEntries(
                Map.entry("totalScore", 82),
                Map.entry("summary", "ok"),
                Map.entry("weakPoints", "[\"system design\"]"),
                Map.entry("strengths", "[\"clear communication\"]"),
                Map.entry("weaknesses", "[\"cache consistency\"]"),
                Map.entry("mainProblems", "[\"cache consistency\"]"),
                Map.entry("reviewSuggestions", "[\"review by topic\"]"),
                Map.entry("suggestions", "[\"keep practicing\"]"),
                Map.entry("qaReview", completeQaReview()),
                Map.entry("rubricScores", formalRubricScores()),
                Map.entry("reportContent", "full report body"));
    }

    private String formalRubricScores() {
        return """
                [{"dimension":"EXPRESSION_STRUCTURE","score":4.1},
                 {"dimension":"TECHNICAL_DEPTH","score":4.1},
                 {"dimension":"BUSINESS_UNDERSTANDING","score":4.1},
                 {"dimension":"RISK_AWARENESS","score":4.1},
                 {"dimension":"IMPLEMENTABILITY","score":4.1}]
                """.replaceAll("\\s+", "");
    }

    private List<InterviewMessage> scoredMessages() {
        InterviewMessage questionOne = new InterviewMessage();
        questionOne.setId(11L);
        questionOne.setRole("AI");
        questionOne.setMessageType("QUESTION");
        questionOne.setQuestionContent("问题一");

        InterviewMessage answerOne = new InterviewMessage();
        answerOne.setId(12L);
        answerOne.setParentMessageId(11L);
        answerOne.setRole("USER");
        answerOne.setMessageType("ANSWER");
        answerOne.setUserAnswer("回答一");

        InterviewMessage evaluationOne = new InterviewMessage();
        evaluationOne.setId(13L);
        evaluationOne.setParentMessageId(11L);
        evaluationOne.setRole("AI");
        evaluationOne.setMessageType("EVALUATION");
        evaluationOne.setAiScore(85);
        evaluationOne.setAiComment("点评一");

        InterviewMessage questionTwo = new InterviewMessage();
        questionTwo.setId(14L);
        questionTwo.setRole("AI");
        questionTwo.setMessageType("QUESTION");
        questionTwo.setQuestionContent("问题二");

        InterviewMessage answerTwo = new InterviewMessage();
        answerTwo.setId(15L);
        answerTwo.setParentMessageId(14L);
        answerTwo.setRole("USER");
        answerTwo.setMessageType("ANSWER");
        answerTwo.setUserAnswer("回答二");

        InterviewMessage evaluationTwo = new InterviewMessage();
        evaluationTwo.setId(16L);
        evaluationTwo.setParentMessageId(14L);
        evaluationTwo.setRole("AI");
        evaluationTwo.setMessageType("EVALUATION");
        evaluationTwo.setAiScore(82);
        evaluationTwo.setAiComment("点评二");

        return List.of(
                questionOne,
                answerOne,
                evaluationOne,
                questionTwo,
                answerTwo,
                evaluationTwo);
    }

    private List<InterviewMessage> answerOnlyMessages() {
        List<InterviewMessage> messages = new java.util.ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            InterviewMessage question = new InterviewMessage();
            question.setId(10L + (index * 2L) - 1L);
            question.setRole("AI");
            question.setMessageType("QUESTION");
            question.setQuestionContent("问题" + index);

            InterviewMessage answer = new InterviewMessage();
            answer.setId(10L + (index * 2L));
            answer.setParentMessageId(question.getId());
            answer.setRole("USER");
            answer.setMessageType("ANSWER");
            answer.setUserAnswer("回答" + (index == 1 ? "一" : index));
            messages.add(question);
            messages.add(answer);
        }
        return List.copyOf(messages);
    }

    private String completeQaReview() {
        return """
                [{"question":"Q1","answer":"A1","score":82,"comment":"点评1"},
                 {"question":"Q2","answer":"A2","score":82,"comment":"点评2"},
                 {"question":"Q3","answer":"A3","score":82,"comment":"点评3"},
                 {"question":"Q4","answer":"A4","score":82,"comment":"点评4"},
                 {"question":"Q5","answer":"A5","score":82,"comment":"点评5"},
                 {"question":"Q6","answer":"A6","score":82,"comment":"点评6"}]
                """.replaceAll("\\s+", "");
    }
}
