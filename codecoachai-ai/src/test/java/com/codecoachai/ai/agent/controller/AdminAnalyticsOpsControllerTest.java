package com.codecoachai.ai.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsJobLogVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsMetricDefinitionVO;
import com.codecoachai.ai.agent.security.AdminOperationConfirmationGuard;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentAnalyticsService;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsOpsControllerTest {

    @Mock
    private AgentV4OpsService agentV4OpsService;
    @Mock
    private AgentAnalyticsService agentAnalyticsService;
    @Mock
    private V4AdminPermissionGuard permissionGuard;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminOperationConfirmationGuard operationConfirmationGuard;
    private AdminAnalyticsOpsController controller;

    @BeforeEach
    void setUp() {
        operationConfirmationGuard = new AdminOperationConfirmationGuard(stringRedisTemplate);
        controller = new AdminAnalyticsOpsController(agentV4OpsService, agentAnalyticsService, permissionGuard, operationConfirmationGuard);
    }

    @Test
    void createMetricRejectsMissingConfirmationBeforeSaving() {
        AdminAnalyticsMetricSaveDTO dto = metricDto();

        assertThrows(BusinessException.class, () -> controller.createMetric(dto));

        verify(permissionGuard).require("admin:analytics:metric:write");
        verify(agentV4OpsService, never()).saveMetric(any());
    }

    @Test
    void metricsDoesNotLoadWhenReadPermissionDenied() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:analytics:agent");

        assertThrows(BusinessException.class,
                () -> controller.metrics(null, null, null, 1L, 10L));

        verify(agentV4OpsService, never()).listMetrics(any(), any());
    }

    @Test
    void createMetricDoesNotConfirmOrSaveWhenWritePermissionDenied() {
        AdminAnalyticsMetricSaveDTO dto = confirmedMetricDto("analytics-metric-create-1234");
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:analytics:metric:write");

        assertThrows(BusinessException.class, () -> controller.createMetric(dto));

        verify(agentV4OpsService, never()).saveMetric(any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void createMetricRequiresConfirmedIdempotencyKey() {
        AdminAnalyticsMetricSaveDTO dto = confirmedMetricDto("analytics-metric-create-1234");
        AnalyticsMetricDefinitionVO saved = new AnalyticsMetricDefinitionVO();
        saved.setId(1L);
        saved.setMetricCode("DAILY_SUCCESS_RATE");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:ANALYTICS_METRIC_CREATE:DAILY_SUCCESS_RATE:analytics-metric-create-1234"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(true);
        when(agentV4OpsService.saveMetric(dto)).thenReturn(saved);

        AnalyticsMetricDefinitionVO result = controller.createMetric(dto).getData();

        assertEquals(1L, result.getId());
        verify(agentV4OpsService).saveMetric(dto);
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void updateMetricUsesPathIdAndReleasesLockWhenSaveFails() {
        AdminAnalyticsMetricSaveDTO dto = confirmedMetricDto("analytics-metric-update-1234");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:ANALYTICS_METRIC_UPDATE:9:analytics-metric-update-1234"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(true);
        doThrow(new IllegalArgumentException("metric invalid"))
                .when(agentV4OpsService).saveMetric(dto);

        assertThrows(IllegalArgumentException.class, () -> controller.updateMetric(9L, dto));

        assertEquals(9L, dto.getId());
        verify(stringRedisTemplate).delete(
                "codecoachai:admin-confirmed-operation:ANALYTICS_METRIC_UPDATE:9:analytics-metric-update-1234");
    }

    @Test
    void metricsThrowsBusinessErrorWhenMetricDictionaryFails() {
        doThrow(new IllegalStateException("metrics unavailable"))
                .when(agentV4OpsService).listMetrics(null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.metrics(null, null, null, 1L, 10L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertEquals("Analytics metrics load failed. Please retry later.", ex.getMessage());
        verify(permissionGuard).require("admin:analytics:agent");
    }

    @Test
    void jobsThrowsBusinessErrorWhenJobPageFails() {
        doThrow(new IllegalStateException("jobs unavailable"))
                .when(agentV4OpsService).pageJobs("AGENT_DAILY_PLAN", "FAILED", 2L, 20L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.jobs("AGENT_DAILY_PLAN", "FAILED", 2L, 20L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertEquals("Analytics jobs load failed. Please retry later.", ex.getMessage());
        verify(permissionGuard).require("admin:analytics:agent");
    }

    @Test
    void rerunRejectsMissingConfirmationBeforeRunning() {
        assertThrows(BusinessException.class, () -> controller.rerun(7L, new AnalyticsJobRunDTO()));

        verify(permissionGuard).require("admin:analytics:job:run");
        verify(agentV4OpsService, never()).rerunJob(any());
    }

    @Test
    void rerunDoesNotConfirmOrRunWhenRunPermissionDenied() {
        AnalyticsJobRunDTO dto = confirmedJobDto("analytics-job-rerun-1234");
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:analytics:job:run");

        assertThrows(BusinessException.class, () -> controller.rerun(7L, dto));

        verify(agentV4OpsService, never()).rerunJob(any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void runDailyPlanRequiresConfirmedIdempotencyKey() {
        AnalyticsJobRunDTO dto = confirmedJobDto("analytics-job-run-1234");
        AnalyticsJobLogVO saved = new AnalyticsJobLogVO();
        saved.setId(3L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:ANALYTICS_JOB_RUN:AGENT_DAILY_PLAN:analytics-job-run-1234"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(true);
        when(agentV4OpsService.runDailyPlanBatch(dto)).thenReturn(saved);

        AnalyticsJobLogVO result = controller.runDailyPlan(dto).getData();

        assertEquals(3L, result.getId());
        verify(agentV4OpsService).runDailyPlanBatch(dto);
    }

    @Test
    void runDailyPlanDoesNotConfirmOrRunWhenRunPermissionDenied() {
        AnalyticsJobRunDTO dto = confirmedJobDto("analytics-job-run-1234");
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:analytics:job:run");

        assertThrows(BusinessException.class, () -> controller.runDailyPlan(dto));

        verify(agentV4OpsService, never()).runDailyPlanBatch(any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    private static AdminAnalyticsMetricSaveDTO metricDto() {
        AdminAnalyticsMetricSaveDTO dto = new AdminAnalyticsMetricSaveDTO();
        dto.setMetricCode("DAILY_SUCCESS_RATE");
        dto.setMetricName("Daily success rate");
        dto.setCategory("AGENT");
        dto.setDefinition("Successful daily plans divided by all attempts");
        dto.setDataSource("agent_run");
        dto.setRefreshFrequency("DAILY");
        dto.setEnabled(1);
        return dto;
    }

    private static AdminAnalyticsMetricSaveDTO confirmedMetricDto(String idempotencyKey) {
        AdminAnalyticsMetricSaveDTO dto = metricDto();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm analytics metric change");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static AnalyticsJobRunDTO confirmedJobDto(String idempotencyKey) {
        AnalyticsJobRunDTO dto = new AnalyticsJobRunDTO();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm analytics job run");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }
}
