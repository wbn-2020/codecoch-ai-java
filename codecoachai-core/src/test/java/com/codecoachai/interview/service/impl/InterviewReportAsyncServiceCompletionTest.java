package com.codecoachai.interview.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.QuestionFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.codecoachai.interview.scenario.InterviewScenarioBinding;
import com.codecoachai.interview.scenario.InterviewScenarioBindingMapper;
import com.codecoachai.interview.scenario.InterviewRubricVersionMapper;
import com.codecoachai.interview.support.InterviewRubricVersion;
import com.codecoachai.task.service.AsyncTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewReportAsyncServiceCompletionTest {

    private static final String MESSAGE_ID = "interview.report:88:token-current";

    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private InterviewScenarioBindingMapper scenarioBindingMapper;
    @Mock
    private InterviewRubricVersionMapper rubricVersionMapper;
    @Mock
    private ResumeFeignClient resumeFeignClient;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private QuestionFeignClient questionFeignClient;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;
    @Mock
    private InterviewMqDispatcher interviewMqDispatcher;
    @Mock
    private AsyncTaskService asyncTaskService;

    private InterviewReportAsyncService service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(InterviewSession.class);
        initTableInfo(InterviewReport.class);
        initTableInfo(InterviewMessage.class);
        initTableInfo(InterviewScenarioBinding.class);
    }

    @BeforeEach
    void setUp() {
        service = new InterviewReportAsyncService(
                sessionMapper,
                reportMapper,
                messageMapper,
                scenarioBindingMapper,
                rubricVersionMapper,
                resumeFeignClient,
                aiFeignClient,
                questionFeignClient,
                agentBusinessActionNotifier,
                new ObjectMapper(),
                interviewMqDispatcher,
                new InterviewReportTransactionService(),
                asyncTaskService);
        when(asyncTaskService.acquireRegistered(any(), eq(3))).thenReturn(true);
        when(sessionMapper.selectById(1L)).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(messages());
        when(reportMapper.selectOne(any())).thenReturn(generatingReport());
        when(reportMapper.update(any(InterviewReport.class), any(Wrapper.class))).thenReturn(1);
    }

    @Test
    void marksAsyncTaskTerminalFailedWhenAiResponseCannotFormConsumableReport() {
        GenerateReportVO incomplete = new GenerateReportVO();
        incomplete.setTotalScore(82);
        incomplete.setRubricScores("[]");
        when(aiFeignClient.report(any())).thenReturn(Result.success(incomplete));
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);

        service.generateReportAsync(1L, 88L, "token-current", MESSAGE_ID);

        verify(asyncTaskService).markTerminalFailed(eq(MESSAGE_ID), any());
        verify(asyncTaskService, never()).markSuccess(eq(MESSAGE_ID), any());
    }

    @Test
    void marksAsyncTaskSuccessOnlyAfterCompleteReportIsPersisted() {
        when(aiFeignClient.report(any())).thenReturn(Result.success(completeReport()));
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);

        service.generateReportAsync(1L, 88L, "token-current", MESSAGE_ID);

        verify(asyncTaskService).markSuccess(eq(MESSAGE_ID), any());
        verify(asyncTaskService, never()).markTerminalFailed(eq(MESSAGE_ID), any());
    }

    @Test
    void failureWriteBackPersistenceErrorRemainsRetryable() {
        when(aiFeignClient.report(any())).thenThrow(new IllegalStateException("AI unavailable"));
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.generateReportAsync(1L, 88L, "token-current", MESSAGE_ID));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Persist interview session failure failed", exception.getMessage());
        verify(asyncTaskService).markFailed(MESSAGE_ID, "Persist interview session failure failed");
        verify(asyncTaskService, never()).markTerminalFailed(eq(MESSAGE_ID), any());
        verify(asyncTaskService, never()).markSuccess(eq(MESSAGE_ID), any());
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(10L);
        return session;
    }

    private InterviewReport generatingReport() {
        InterviewReport report = new InterviewReport();
        report.setId(88L);
        report.setSessionId(1L);
        report.setUserId(10L);
        report.setStatus("GENERATING");
        report.setGenerationToken("token-current");
        return report;
    }

    private GenerateReportVO completeReport() {
        GenerateReportVO report = new GenerateReportVO();
        report.setTotalScore(82);
        report.setSummary("报告摘要");
        report.setStrengths("[\"表达清晰\"]");
        report.setWeaknesses("缓存一致性边界说明不足");
        report.setMainProblems("[\"缺少失败场景分析\"]");
        report.setReviewSuggestions("[\"补充缓存一致性案例\"]");
        report.setSuggestions("[\"补充缓存一致性案例\"]");
        report.setQaReview(completeQaReview());
        report.setRubricScores("""
                [{"dimension":"EXPRESSION_STRUCTURE","score":4.1},
                 {"dimension":"TECHNICAL_DEPTH","score":4.1},
                 {"dimension":"BUSINESS_UNDERSTANDING","score":4.1},
                 {"dimension":"RISK_AWARENESS","score":4.1},
                 {"dimension":"IMPLEMENTABILITY","score":4.1}]
                """.replaceAll("\\s+", ""));
        report.setReportContent("报告正文");
        return report;
    }

    private java.util.List<InterviewMessage> messages() {
        java.util.List<InterviewMessage> messages = new java.util.ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            InterviewMessage question = new InterviewMessage();
            question.setId(10L + (index * 2L) - 1L);
            question.setRole("AI");
            question.setMessageType("QUESTION");
            question.setQuestionContent("Q" + index);

            InterviewMessage answer = new InterviewMessage();
            answer.setId(10L + (index * 2L));
            answer.setParentMessageId(question.getId());
            answer.setRole("USER");
            answer.setMessageType("ANSWER");
            answer.setUserAnswer("A" + index);
            answer.setScore(82);
            answer.setComment("C" + index);
            messages.add(question);
            messages.add(answer);
        }
        return java.util.List.copyOf(messages);
    }

    private String completeQaReview() {
        return """
                [{"question":"Q1","answer":"A1","score":82,"comment":"C1"},
                 {"question":"Q2","answer":"A2","score":82,"comment":"C2"},
                 {"question":"Q3","answer":"A3","score":82,"comment":"C3"},
                 {"question":"Q4","answer":"A4","score":82,"comment":"C4"},
                 {"question":"Q5","answer":"A5","score":82,"comment":"C5"},
                 {"question":"Q6","answer":"A6","score":82,"comment":"C6"}]
                """.replaceAll("\\s+", "");
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }
}
