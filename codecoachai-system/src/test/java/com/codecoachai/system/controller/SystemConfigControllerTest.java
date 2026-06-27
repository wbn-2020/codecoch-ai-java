package com.codecoachai.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.system.domain.dto.SystemConfigQueryDTO;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import java.util.List;
import com.codecoachai.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemConfigControllerTest {

    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private SystemConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new SystemConfigController(systemConfigService, permissionGuard, operationConfirmationGuard);
    }

    @Test
    void updateConfigByIdUsesExplicitIdSemantics() {
        Long id = 7L;
        SystemConfigSaveDTO dto = new SystemConfigSaveDTO();
        dto.setConfigValue("updated");
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm update by id");
        dto.setIdempotencyKey("system-config-update-7");

        SystemConfigVO vo = new SystemConfigVO();
        vo.setId(id);
        when(operationConfirmationGuard.requireConfirmed(
                eq("system-config-update:id:7"),
                eq(true),
                eq(false),
                eq("confirm update by id"),
                eq("system-config-update-7"))).thenReturn("config-update-lock");
        when(systemConfigService.updateConfigById(id, dto)).thenReturn(vo);

        SystemConfigVO result = controller.updateConfigById(id, dto).getData();

        assertEquals(id, result.getId());
        verify(permissionGuard).require("admin:system:config:write");
        verify(systemConfigService).updateConfigById(id, dto);
    }

    @Test
    void listConfigsReturnsPagedResultFromService() {
        SystemConfigQueryDTO query = new SystemConfigQueryDTO();
        query.setPageNo(2L);
        query.setPageSize(20L);
        query.setKeyword("timeout");
        query.setConfigType("STRING");
        query.setStatus(1);

        SystemConfigVO record = new SystemConfigVO();
        record.setId(9L);
        record.setConfigKey("system.timeout");
        PageResult<SystemConfigVO> page = PageResult.of(List.of(record), 21L, 2L, 20L);
        when(systemConfigService.pageConfigs(query)).thenReturn(page);

        PageResult<SystemConfigVO> result = controller.listConfigs(query).getData();

        assertEquals(21L, result.getTotal());
        assertEquals(2L, result.getPageNo());
        assertEquals(20L, result.getPageSize());
        assertEquals(1, result.getRecords().size());
        verify(permissionGuard).require("admin:system:config:list");
        verify(systemConfigService).pageConfigs(query);
    }
}
