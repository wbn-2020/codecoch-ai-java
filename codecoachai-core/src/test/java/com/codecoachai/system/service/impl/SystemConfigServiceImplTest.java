package com.codecoachai.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.List;
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

    private SystemConfigServiceImpl systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigService = new SystemConfigServiceImpl(
                systemConfigMapper,
                jdbcTemplate,
                redisConnectionFactoryProvider);
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
