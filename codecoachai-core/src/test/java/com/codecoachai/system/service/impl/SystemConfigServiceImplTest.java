package com.codecoachai.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.system.domain.dto.SystemConfigQueryDTO;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.entity.SystemConfig;
import com.codecoachai.system.domain.vo.AdminDashboardOverviewVO;
import com.codecoachai.system.mapper.SystemConfigMapper;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    @Mock
    private HttpClient healthHttpClient;

    private SystemConfigServiceImpl systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigService = new SystemConfigServiceImpl(
                systemConfigMapper,
                jdbcTemplate,
                redisConnectionFactoryProvider,
                healthHttpClient);
    }

    @Test
    void updateSensitiveConfigWithBlankValueKeepsExistingSecret() {
        SystemConfig existing = existingConfig("openai.api_key", "sk-live-existing");
        when(systemConfigMapper.selectById(1L)).thenReturn(existing);

        SystemConfigSaveDTO dto = updateDto("openai.api_key", "");

        systemConfigService.updateConfig("1", dto);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).updateById(captor.capture());
        assertEquals("sk-live-existing", captor.getValue().getConfigValue());
    }

    @Test
    void updateNonSensitiveConfigWithBlankValueStillPersistsBlank() {
        SystemConfig existing = existingConfig("interview.question.limit", "20");
        when(systemConfigMapper.selectById(1L)).thenReturn(existing);

        SystemConfigSaveDTO dto = updateDto("interview.question.limit", "");

        systemConfigService.updateConfig("1", dto);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).updateById(captor.capture());
        assertEquals("", captor.getValue().getConfigValue());
    }

    @Test
    void pageConfigsFiltersByExactConfigKey() {
        String configKey = "acceptance.governance.20260728.audit-switch";
        SystemConfigQueryDTO query = new SystemConfigQueryDTO();
        query.setConfigKey(configKey);
        Page<SystemConfig> page = new Page<>(1L, 10L, 0L);
        page.setRecords(List.of());
        when(systemConfigMapper.selectPage(any(Page.class), any())).thenReturn(page);

        systemConfigService.pageConfigs(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<SystemConfig>> captor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(systemConfigMapper).selectPage(any(Page.class), captor.capture());
        initializeTableInfo(SystemConfig.class);
        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("config_key"));
        assertEquals(configKey, captor.getValue().getParamNameValuePairs().values().iterator().next());
    }

    @Test
    void dashboardMetricsExposeUnavailableSourcesInsteadOfFabricatedZeroes() {
        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        AdminDashboardOverviewVO.OpsMetricsVO metrics = dashboard.getSystemStatus().getOpsMetrics();
        assertNotNull(metrics);
        assertEquals("UNAVAILABLE", metrics.getTrafficMetricsStatus());
        assertNull(metrics.getQps());
        assertNull(metrics.getTps());
        assertNull(metrics.getRpm());
        assertNull(metrics.getTpm());
        assertEquals("NOT_CONFIGURED", metrics.getRedisMetricsStatus());
        assertNull(metrics.getRedisHitRate());
        assertTrue(List.of("AVAILABLE", "PARTIAL").contains(metrics.getJvmMetricsStatus()));
        assertNotNull(metrics.getHeapUsedMb());
    }

    @Test
    void dashboardMetadataCacheIsRequestScopedAndDeduplicatesLookups() {
        String tableExistsSql =
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        String columnExistsSql = "SELECT COUNT(1) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        lenient().when(jdbcTemplate.queryForObject(eq(tableExistsSql), eq(Long.class), any()))
                .thenReturn(1L);
        lenient().when(jdbcTemplate.queryForObject(eq(columnExistsSql), eq(Long.class), any(), any()))
                .thenReturn(1L);

        systemConfigService.dashboardOverview();

        verify(jdbcTemplate, times(1))
                .queryForObject(tableExistsSql, Long.class, "ai_call_log");
        verify(jdbcTemplate, times(1))
                .queryForObject(columnExistsSql, Long.class, "ai_call_log", "status");

        systemConfigService.dashboardOverview();

        verify(jdbcTemplate, times(2))
                .queryForObject(tableExistsSql, Long.class, "ai_call_log");
        verify(jdbcTemplate, times(2))
                .queryForObject(columnExistsSql, Long.class, "ai_call_log", "status");
    }

    @Test
    void missingHealthConfigurationIsDiagnosableAndCannotReportHealthy() throws Exception {
        lenient().when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        setHealthServices("");

        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        AdminDashboardOverviewVO.ServiceStatusVO configStatus = dashboard.getSystemStatus().getServices().stream()
                .filter(service -> "health-config".equals(service.getServiceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("UNKNOWN", configStatus.getStatus());
        assertTrue(configStatus.getReason().contains("未配置"));
        assertEquals("UNKNOWN", dashboard.getSystemStatus().getStatus());
    }

    @Test
    void malformedHealthConfigurationIsDiagnosableAndCannotReportHealthy() throws Exception {
        lenient().when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        setHealthServices("codecoachai-ai|codecoachai-ai|70000|service");

        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        AdminDashboardOverviewVO.ServiceStatusVO configStatus = serviceStatus(dashboard, "health-config");
        assertEquals("UNKNOWN", configStatus.getStatus());
        assertTrue(configStatus.getReason().contains("端口超出有效范围"));
        assertEquals("UNKNOWN", dashboard.getSystemStatus().getStatus());
    }

    @Test
    void unknownCriticalServiceCannotReportHealthy() throws Exception {
        lenient().when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        setHealthServices("codecoachai-ai|codecoachai-ai|9206|service");
        when(healthHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unreachable")));

        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        AdminDashboardOverviewVO.ServiceStatusVO aiStatus = serviceStatus(dashboard, "codecoachai-ai");
        assertEquals("UNKNOWN", aiStatus.getStatus());
        assertTrue(aiStatus.getReason().contains("IllegalStateException"));
        assertEquals("UNKNOWN", dashboard.getSystemStatus().getStatus());
    }

    @Test
    void degradedCriticalServiceDegradesOverallStatus() throws Exception {
        lenient().when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        setHealthServices("codecoachai-search|codecoachai-search|8091|service");
        HttpResponse<String> response = response(200, "{\"status\":\"DOWN\"}");
        when(healthHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        assertEquals("DEGRADED", serviceStatus(dashboard, "codecoachai-search").getStatus());
        assertEquals("DEGRADED", dashboard.getSystemStatus().getStatus());
    }

    @Test
    void nestedHealthyComponentCannotOverrideTopLevelDownActuatorStatus() throws Exception {
        lenient().when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        setHealthServices("codecoachai-search|codecoachai-search|8091|service");
        HttpResponse<String> response = response(
                200,
                "{\"status\":\"DOWN\",\"components\":{\"diskSpace\":{\"status\":\"UP\"}}}");
        when(healthHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        assertEquals("DEGRADED", serviceStatus(dashboard, "codecoachai-search").getStatus());
        assertEquals("DEGRADED", dashboard.getSystemStatus().getStatus());
    }

    @Test
    void reachableGatewayPortDoesNotMaskActuatorFailure() throws Exception {
        lenient().when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        setHealthServices("codecoachai-gateway|codecoachai-gateway|8080|gateway");
        HttpResponse<String> actuatorResponse = response(200, "{\"status\":\"DOWN\"}");
        HttpResponse<String> gatewayResponse = response(200, "gateway");
        when(healthHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(actuatorResponse),
                        CompletableFuture.completedFuture(gatewayResponse)
                );

        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        AdminDashboardOverviewVO.ServiceStatusVO gatewayStatus =
                serviceStatus(dashboard, "codecoachai-gateway");
        assertEquals("DEGRADED", gatewayStatus.getStatus());
        assertTrue(gatewayStatus.getReason().contains("Actuator"));
        assertEquals("DEGRADED", dashboard.getSystemStatus().getStatus());
    }

    @Test
    void healthTargetsStartInParallelBeforeResultsAreJoined() throws Exception {
        lenient().when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        setHealthServices(
                "gateway|gateway|8080|service,"
                        + "core|core|9200|service,"
                        + "ai|ai|9206|service,"
                        + "search|search|8091|service"
        );
        HttpResponse<String> healthyResponse = response(200, "{\"status\":\"UP\"}");
        AtomicInteger started = new AtomicInteger();
        List<CompletableFuture<HttpResponse<String>>> pending = new ArrayList<>();
        when(healthHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
                    pending.add(future);
                    if (started.incrementAndGet() == 4) {
                        pending.forEach(item -> item.complete(healthyResponse));
                    }
                    return future;
                });

        AdminDashboardOverviewVO dashboard = systemConfigService.dashboardOverview();

        assertEquals(4, started.get());
        assertEquals("HEALTHY", dashboard.getSystemStatus().getStatus());
    }

    private void setHealthServices(String value) throws Exception {
        Field field = SystemConfigServiceImpl.class.getDeclaredField("healthServices");
        field.setAccessible(true);
        field.set(systemConfigService, value);
    }

    private static AdminDashboardOverviewVO.ServiceStatusVO serviceStatus(
            AdminDashboardOverviewVO dashboard,
            String serviceName) {
        return dashboard.getSystemStatus().getServices().stream()
                .filter(service -> serviceName.equals(service.getServiceName()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    private static void initializeTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    private static SystemConfig existingConfig(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setId(1L);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setValueType("STRING");
        config.setStatus(1);
        return config;
    }

    private static SystemConfigSaveDTO updateDto(String key, String value) {
        SystemConfigSaveDTO dto = new SystemConfigSaveDTO();
        dto.setConfigKey(key);
        dto.setConfigValue(value);
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm system config change");
        dto.setIdempotencyKey("system-config-update-1234");
        return dto;
    }
}
