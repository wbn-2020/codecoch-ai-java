package com.codecoachai.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.system.domain.entity.LoginLog;
import com.codecoachai.system.domain.entity.OperationLog;
import com.codecoachai.system.mapper.LoginLogMapper;
import com.codecoachai.system.mapper.OperationLogMapper;
import com.codecoachai.system.mapper.SlowSqlLogMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminLogControllerTest {

    @Mock
    private LoginLogMapper loginLogMapper;
    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private SlowSqlLogMapper slowSqlLogMapper;
    @Mock
    private AdminPermissionGuard permissionGuard;

    private AdminLogController controller;

    @BeforeEach
    void setUp() {
        initTableInfo(LoginLog.class);
        initTableInfo(OperationLog.class);
        controller = new AdminLogController(loginLogMapper, operationLogMapper, slowSqlLogMapper, permissionGuard);
    }

    @Test
    void loginLogResponseKeepsFullTraceIdAndAddsShortTraceId() {
        String traceId = "trace-20260810-0123456789abcdef";
        LoginLog log = new LoginLog();
        log.setTraceId(traceId);
        Page<LoginLog> page = Page.of(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(log));
        when(loginLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = controller.pageLoginLogs(1L, 20L, null, null, null, traceId,
                null, null, null, null, null).getData();

        var vo = result.getRecords().get(0);
        assertEquals(traceId, vo.getTraceId());
        assertEquals(traceId.substring(0, 12), vo.getTraceIdShort());
        assertEquals(vo.getTraceIdShort(), vo.getShortTraceId());
        verify(permissionGuard).require("admin:audit:login-log");
    }

    @Test
    void operationLogResponseKeepsFullTraceIdAndAddsShortTraceId() {
        String traceId = "trace-20260810-0123456789abcdef";
        OperationLog log = new OperationLog();
        log.setTraceId(traceId);
        Page<OperationLog> page = Page.of(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(log));
        when(operationLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = controller.pageOperationLogs(1L, 20L, null, null, null,
                null, null, traceId, null, null, null).getData();

        var vo = result.getRecords().get(0);
        assertEquals(traceId, vo.getTraceId());
        assertEquals(traceId.substring(0, 12), vo.getTraceIdShort());
        assertEquals(vo.getTraceIdShort(), vo.getShortTraceId());
        verify(permissionGuard).require("admin:audit:operation-log");
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
