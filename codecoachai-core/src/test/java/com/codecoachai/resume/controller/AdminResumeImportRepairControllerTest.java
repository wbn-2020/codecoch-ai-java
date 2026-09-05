package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRequestDTO;
import com.codecoachai.resume.domain.vo.ResumeImportRepairResultVO;
import com.codecoachai.resume.service.ResumeImportHistoricalRepairService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminResumeImportRepairControllerTest {

    @Mock
    private ResumeImportHistoricalRepairService repairService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminResumeImportRepairController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminResumeImportRepairController(
                repairService, permissionGuard, operationConfirmationGuard);
    }

    @AfterEach
    void tearDown() {
        com.codecoachai.common.security.context.LoginUserContext.clear();
    }

    @Test
    void defaultRequestIsDryRunAndDoesNotAcquireExecutionConfirmation() {
        ResumeImportRepairRequestDTO request = request();
        ResumeImportRepairResultVO expected = new ResumeImportRepairResultVO();
        expected.setDryRun(true);
        when(repairService.repair(eq(request), any())).thenReturn(expected);

        ResumeImportRepairResultVO result = controller.repair(request).getData();

        assertEquals(true, result.isDryRun());
        verify(permissionGuard).require("admin:system:overview");
        verify(operationConfirmationGuard, never()).requireConfirmed(
                any(), any(), any(), any(), any());
        verify(repairService).repair(eq(request), any());
    }

    @Test
    void executionRequiresConfirmedNonDryRunRequest() {
        ResumeImportRepairRequestDTO request = request();
        request.setDryRun(false);
        request.setConfirm(true);
        request.setReason("repair confirmed historical records");
        request.setIdempotencyKey("resume-repair-1001-request");
        ResumeImportRepairResultVO expected = new ResumeImportRepairResultVO();
        expected.setDryRun(false);
        when(operationConfirmationGuard.requireConfirmed(
                "RESUME_IMPORT_REPAIR:resume-repair-1001",
                true,
                false,
                "repair confirmed historical records",
                "resume-repair-1001-request")).thenReturn("repair-lock");
        when(repairService.repair(eq(request), any())).thenReturn(expected);

        ResumeImportRepairResultVO result = controller.repair(request).getData();

        assertEquals(false, result.isDryRun());
        verify(operationConfirmationGuard).requireConfirmed(
                "RESUME_IMPORT_REPAIR:resume-repair-1001",
                true,
                false,
                "repair confirmed historical records",
                "resume-repair-1001-request");
        verify(repairService).repair(eq(request), any());
    }

    private ResumeImportRepairRequestDTO request() {
        ResumeImportRepairRequestDTO request = new ResumeImportRepairRequestDTO();
        request.setRepairBatchId("resume-repair-1001");
        request.setAnalysisRecordIds(java.util.List.of(1001L));
        return request;
    }
}
