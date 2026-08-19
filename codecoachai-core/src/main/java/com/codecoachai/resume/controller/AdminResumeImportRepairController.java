package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRollbackDTO;
import com.codecoachai.resume.domain.vo.ResumeImportRepairResultVO;
import com.codecoachai.resume.service.ResumeImportHistoricalRepairService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Resume Import Repair")
@RestController
@RequestMapping("/admin/resume-import-repairs")
@RequiredArgsConstructor
public class AdminResumeImportRepairController {

    // This is intentionally restricted to administrator baseline permission until a dedicated
    // RBAC button is delivered with the administration remediation screen.
    private static final String PERMISSION = "admin:system:overview";

    private final ResumeImportHistoricalRepairService repairService;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @Operation(summary = "Preview or repair historical resume import records")
    @PostMapping
    @OperationLog(module = "resume", action = "REPAIR_HISTORICAL_RESUME_IMPORT",
            description = "盘点或修复历史简历导入记录", logArgs = false, logResponse = false)
    public Result<ResumeImportRepairResultVO> repair(
            @RequestBody(required = false) ResumeImportRepairRequestDTO request) {
        permissionGuard.require(PERMISSION);
        ResumeImportRepairRequestDTO safeRequest =
                request == null ? new ResumeImportRepairRequestDTO() : request;
        boolean dryRun = !Boolean.FALSE.equals(safeRequest.getDryRun());
        String lockKey = null;
        if (!dryRun) {
            lockKey = operationConfirmationGuard.requireConfirmed(
                    "RESUME_IMPORT_REPAIR:" + safeRequest.getRepairBatchId(),
                    safeRequest.getConfirm(),
                    Boolean.FALSE,
                    safeRequest.getReason(),
                    safeRequest.getIdempotencyKey());
        }
        try {
            return Result.success(repairService.repair(safeRequest, LoginUserContext.getUserId()));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Operation(summary = "Preview or rollback one historical resume import repair batch")
    @PostMapping("/{repairBatchId}/rollback")
    @OperationLog(module = "resume", action = "ROLLBACK_HISTORICAL_RESUME_IMPORT_REPAIR",
            description = "预览或回滚历史简历导入修复", logArgs = false, logResponse = false)
    public Result<ResumeImportRepairResultVO> rollback(
            @PathVariable String repairBatchId,
            @RequestBody(required = false) ResumeImportRepairRollbackDTO request) {
        permissionGuard.require(PERMISSION);
        ResumeImportRepairRollbackDTO safeRequest =
                request == null ? new ResumeImportRepairRollbackDTO() : request;
        boolean dryRun = !Boolean.FALSE.equals(safeRequest.getDryRun());
        String lockKey = null;
        if (!dryRun) {
            lockKey = operationConfirmationGuard.requireConfirmed(
                    "RESUME_IMPORT_REPAIR_ROLLBACK:" + repairBatchId,
                    safeRequest.getConfirm(),
                    Boolean.FALSE,
                    safeRequest.getReason(),
                    safeRequest.getIdempotencyKey());
        }
        try {
            return Result.success(repairService.rollback(
                    repairBatchId, safeRequest, LoginUserContext.getUserId()));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }
}
