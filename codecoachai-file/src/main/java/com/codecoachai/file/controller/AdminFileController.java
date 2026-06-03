package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.file.domain.dto.AdminFileQueryDTO;
import com.codecoachai.file.domain.vo.FileInfoVO;
import com.codecoachai.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        permissionGuard.require(PERM_FILE_DOWNLOAD);
        return fileStorageService.adminDownload(id);
    }
}
