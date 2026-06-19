package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.vo.InterviewReportAgentEvidenceVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.codecoachai.interview.service.impl.AgentBusinessActionNotifier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
                agentBusinessActionNotifier);
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
    void completeReportCompletesAgentInterviewTaskWithReportEvidence() {
        when(sessionMapper.selectById(1L)).thenReturn(targetJobSession());
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(reportMapper.insert(any(InterviewReport.class))).thenAnswer(invocation -> {
            InterviewReport report = invocation.getArgument(0);
            report.setId(88L);
            return 1;
        });
        InnerInterviewReportController.CompleteReportDTO dto =
                new InnerInterviewReportController.CompleteReportDTO();
        dto.setReportStatus("SUCCESS");
        dto.setReportJson("{\"summary\":\"ok\"}");
        dto.setTotalScore(82);

        controller.completeReport(1L, dto);

        verify(agentBusinessActionNotifier).completeInterviewReport(10L, 300L, 88L);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
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
        return report;
    }
}
