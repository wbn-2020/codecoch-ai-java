package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.file.domain.dto.AdminFileDownloadAccessDTO;
import com.codecoachai.file.domain.dto.AdminFileQueryDTO;
import com.codecoachai.file.domain.vo.FileInfoVO;
import com.codecoachai.file.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/files")
public class AdminFileController {

    private static final String PERM_FILE_LIST = "admin:file:list";
    private static final String PERM_FILE_DOWNLOAD = "admin:file:download";

    private final FileStorageService fileStorageService;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping
    public Result<PageResult<FileInfoVO>> page(@ModelAttribute AdminFileQueryDTO query) {
        permissionGuard.require(PERM_FILE_LIST);
        return Result.success(fileStorageService.pageAdminFiles(query));
    }

    @GetMapping("/{id}")
    public Result<FileInfoVO> detail(@PathVariable Long id) {
        permissionGuard.require(PERM_FILE_LIST);
        return Result.success(fileStorageService.getAdminFile(id));
    }

    @GetMapping("/{id}/download")
    @OperationLog(module = "file", action = "DOWNLOAD_ADMIN_FILE", description = "管理端下载用户文件", logResponse = false)
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        permissionGuard.require(PERM_FILE_DOWNLOAD);
        throw new BusinessException(ErrorCode.PARAM_ERROR,
                "Admin file download requires POST with confirmSensitiveAccess=true, confirm=true, dryRun=false, reason, and idempotencyKey.");
    }

    @PostMapping("/{id}/download")
    @OperationLog(module = "file", action = "DOWNLOAD_ADMIN_FILE", description = "Admin user file download", logArgs = true, logResponse = false)
    public ResponseEntity<Resource> downloadConfirmed(@PathVariable Long id,
                                                     @Valid @RequestBody AdminFileDownloadAccessDTO dto) {
        permissionGuard.require(PERM_FILE_DOWNLOAD);
        String lockKey = acquireDownloadAccess(id, dto);
        try {
            return fileStorageService.adminDownload(id);
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    private String acquireDownloadAccess(Long fileId, AdminFileDownloadAccessDTO dto) {
        if (dto == null || !dto.isConfirmSensitiveAccess()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Admin file download requires confirmSensitiveAccess=true.");
        }
        return operationConfirmationGuard.requireConfirmed("admin-file-download:" + fileId,
                dto.getConfirm(), dto.getDryRun(), dto.effectiveReason(), dto.getIdempotencyKey());
    }
}
