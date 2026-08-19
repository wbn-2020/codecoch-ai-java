package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.ReadinessRepairRequestDTO;
import com.codecoachai.resume.domain.vo.ReadinessRepairResultVO;
import com.codecoachai.resume.service.ReadinessHistoricalRepairService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Readiness Repair")
@RestController
@RequestMapping("/admin/readiness-repairs")
@RequiredArgsConstructor
public class AdminReadinessRepairController {

    private static final String PERMISSION = "admin:system:overview";

    private final ReadinessHistoricalRepairService repairService;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @Operation(summary = "Preview or regenerate invalid readiness snapshots")
    @PostMapping
    @OperationLog(module = "resume", action = "REPAIR_HISTORICAL_READINESS",
            description = "盘点或重建历史岗位准备度快照", logArgs = false, logResponse = false)
    public Result<ReadinessRepairResultVO> repair(
            @RequestBody(required = false) ReadinessRepairRequestDTO request) {
        permissionGuard.require(PERMISSION);
        ReadinessRepairRequestDTO safeRequest =
                request == null ? new ReadinessRepairRequestDTO() : request;
        boolean dryRun = !Boolean.FALSE.equals(safeRequest.getDryRun());
        String lockKey = null;
        if (!dryRun) {
            lockKey = operationConfirmationGuard.requireConfirmed(
                    "READINESS_REPAIR:" + safeRequest.getRepairBatchId(),
                    safeRequest.getConfirm(),
                    Boolean.FALSE,
                    safeRequest.getReason(),
                    safeRequest.getIdempotencyKey());
        }
        try {
            return Result.success(repairService.repair(safeRequest));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }
}
