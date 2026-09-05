package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.resume.domain.dto.ReadinessRepairRequestDTO;
import com.codecoachai.resume.domain.vo.ReadinessRepairResultVO;
import com.codecoachai.resume.service.ReadinessHistoricalRepairService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminReadinessRepairControllerTest {

    @Mock
    private ReadinessHistoricalRepairService repairService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminReadinessRepairController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminReadinessRepairController(
                repairService, permissionGuard, operationConfirmationGuard);
    }

    @Test
    void defaultRequestRemainsDryRunWithoutExecutionConfirmation() {
        ReadinessRepairRequestDTO request = request();
        ReadinessRepairResultVO expected = new ReadinessRepairResultVO();
        expected.setDryRun(true);
        when(repairService.repair(request)).thenReturn(expected);

        ReadinessRepairResultVO result = controller.repair(request).getData();

        assertTrue(result.isDryRun());
        verify(permissionGuard).require("admin:system:overview");
        verify(operationConfirmationGuard, never()).requireConfirmed(
                any(), any(), any(), any(), any());
        verify(repairService).repair(request);
    }

    @Test
    void executionRequiresConfirmationReasonAndIdempotencyKey() {
        ReadinessRepairRequestDTO request = request();
        request.setDryRun(false);
        request.setConfirm(true);
        request.setReason("repair invalid readiness snapshots");
        request.setIdempotencyKey("readiness-repair-1001-request");
        ReadinessRepairResultVO expected = new ReadinessRepairResultVO();
        expected.setDryRun(false);
        when(operationConfirmationGuard.requireConfirmed(
                "READINESS_REPAIR:readiness-repair-1001",
                true,
                false,
                "repair invalid readiness snapshots",
                "readiness-repair-1001-request")).thenReturn("repair-lock");
        when(repairService.repair(request)).thenReturn(expected);

        ReadinessRepairResultVO result = controller.repair(request).getData();

        assertFalse(result.isDryRun());
        verify(operationConfirmationGuard).requireConfirmed(
                "READINESS_REPAIR:readiness-repair-1001",
                true,
                false,
                "repair invalid readiness snapshots",
                "readiness-repair-1001-request");
        verify(repairService).repair(eq(request));
    }

    private ReadinessRepairRequestDTO request() {
        ReadinessRepairRequestDTO request = new ReadinessRepairRequestDTO();
        request.setRepairBatchId("readiness-repair-1001");
        request.setSnapshotIds(java.util.List.of(1001L));
        return request;
    }
}
