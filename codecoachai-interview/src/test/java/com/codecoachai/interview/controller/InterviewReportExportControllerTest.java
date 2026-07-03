package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewReportExportControllerTest {

    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;

    private InterviewReportExportController controller;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(InterviewSession.class);
        initTableInfo(InterviewReport.class);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    @BeforeEach
    void setUp() {
        controller = new InterviewReportExportController(reportMapper, sessionMapper, new ObjectMapper());
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("tester").build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void radarChartQueriesLatestGeneratedNonDeletedReport() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(10L);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        InterviewReport report = new InterviewReport();
        report.setId(88L);
        report.setSessionId(1L);
        report.setUserId(10L);
        report.setStatus("GENERATED");
        report.setTotalScore(90);
        when(reportMapper.selectOne(any())).thenReturn(report);

        controller.radarChart(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<InterviewReport>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(reportMapper).selectOne(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("deleted"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("ORDER BY"));
    }

    @Test
    void radarChartRejectsWhenNoGeneratedReportExists() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(10L);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> controller.radarChart(1L));
    }
}
