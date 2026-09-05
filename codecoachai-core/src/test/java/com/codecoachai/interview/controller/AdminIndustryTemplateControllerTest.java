package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.interview.domain.dto.IndustryTemplateCreateDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateUpdateDTO;
import com.codecoachai.interview.domain.vo.IndustryTemplateVO;
import com.codecoachai.interview.service.IndustryTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminIndustryTemplateControllerTest {

    @Mock
    private IndustryTemplateService industryTemplateService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminIndustryTemplateController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminIndustryTemplateController(
                industryTemplateService,
                permissionGuard,
                operationConfirmationGuard);
    }

    @Test
    void createForwardsDryRunToConfirmationGuard() {
        IndustryTemplateCreateDTO dto = createDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("industry-template-create:ECOMMERCE"),
                eq(true),
                eq(false),
                eq("create industry template"),
                eq("industry-create-1234")))
                .thenReturn("lock-key");
        when(industryTemplateService.create(dto)).thenReturn(templateVO());

        controller.create(dto);

        verify(permissionGuard).require("admin:industry-template:write");
        verify(industryTemplateService).create(dto);
    }

    @Test
    void updateForwardsDryRunToConfirmationGuard() {
        IndustryTemplateUpdateDTO dto = updateDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("industry-template-update:12"),
                eq(true),
                eq(false),
                eq("update industry template"),
                eq("industry-update-1234")))
                .thenReturn("lock-key");
        when(industryTemplateService.update(12L, dto)).thenReturn(templateVO());

        controller.update(12L, dto);

        verify(permissionGuard).require("admin:industry-template:write");
        verify(industryTemplateService).update(12L, dto);
    }

    @Test
    void enableForwardsDryRunToConfirmationGuard() {
        AdminIndustryTemplateController.AdminOperationConfirmDTO dto =
                confirmDto("enable industry template", "industry-enable-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("industry-template-enable:12"),
                eq(true),
                eq(false),
                eq("enable industry template"),
                eq("industry-enable-1234")))
                .thenReturn("lock-key");

        controller.enable(12L, dto);

        verify(permissionGuard).require("admin:industry-template:write");
        verify(industryTemplateService).enable(12L);
    }

    @Test
    void disableForwardsDryRunToConfirmationGuard() {
        AdminIndustryTemplateController.AdminOperationConfirmDTO dto =
                confirmDto("disable industry template", "industry-disable-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("industry-template-disable:12"),
                eq(true),
                eq(false),
                eq("disable industry template"),
                eq("industry-disable-1234")))
                .thenReturn("lock-key");

        controller.disable(12L, dto);

        verify(permissionGuard).require("admin:industry-template:write");
        verify(industryTemplateService).disable(12L);
    }

    @Test
    void deleteForwardsDryRunToConfirmationGuard() {
        AdminIndustryTemplateController.AdminOperationConfirmDTO dto =
                confirmDto("delete industry template", "industry-delete-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("industry-template-delete:12"),
                eq(true),
                eq(false),
                eq("delete industry template"),
                eq("industry-delete-1234")))
                .thenReturn("lock-key");

        controller.delete(12L, dto);

        verify(permissionGuard).require("admin:industry-template:write");
        verify(industryTemplateService).delete(12L);
    }

    @Test
    void createDoesNotExecuteServiceWhenGuardRejectsDryRun() {
        IndustryTemplateCreateDTO dto = createDto(true);
        when(operationConfirmationGuard.requireConfirmed(
                eq("industry-template-create:ECOMMERCE"),
                eq(true),
                eq(true),
                eq("create industry template"),
                eq("industry-create-1234")))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dryRun requests are blocked"));

        assertThrows(BusinessException.class, () -> controller.create(dto));

        verify(permissionGuard).require("admin:industry-template:write");
        verify(industryTemplateService, never()).create(dto);
    }

    @Test
    void deleteReleasesIdempotencyLockWhenServiceFails() {
        AdminIndustryTemplateController.AdminOperationConfirmDTO dto =
                confirmDto("delete industry template", "industry-delete-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("industry-template-delete:12"),
                eq(true),
                eq(false),
                eq("delete industry template"),
                eq("industry-delete-1234")))
                .thenReturn("lock-key");
        doThrow(new RuntimeException("database unavailable"))
                .when(industryTemplateService).delete(12L);

        assertThrows(RuntimeException.class, () -> controller.delete(12L, dto));

        verify(operationConfirmationGuard).release("lock-key");
    }

    private static IndustryTemplateCreateDTO createDto(boolean dryRun) {
        IndustryTemplateCreateDTO dto = new IndustryTemplateCreateDTO();
        dto.setIndustryCode("ECOMMERCE");
        dto.setIndustryName("E-Commerce");
        dto.setEnabled(1);
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("create industry template");
        dto.setIdempotencyKey("industry-create-1234");
        return dto;
    }

    private static IndustryTemplateUpdateDTO updateDto(boolean dryRun) {
        IndustryTemplateUpdateDTO dto = new IndustryTemplateUpdateDTO();
        dto.setIndustryCode("ECOMMERCE");
        dto.setIndustryName("E-Commerce");
        dto.setEnabled(1);
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("update industry template");
        dto.setIdempotencyKey("industry-update-1234");
        return dto;
    }

    private static AdminIndustryTemplateController.AdminOperationConfirmDTO confirmDto(
            String reason, String idempotencyKey) {
        AdminIndustryTemplateController.AdminOperationConfirmDTO dto =
                new AdminIndustryTemplateController.AdminOperationConfirmDTO();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason(reason);
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static IndustryTemplateVO templateVO() {
        IndustryTemplateVO vo = new IndustryTemplateVO();
        vo.setIndustryTemplateId(12L);
        vo.setIndustryCode("ECOMMERCE");
        vo.setIndustryName("E-Commerce");
        vo.setEnabled(1);
        return vo;
    }
}
