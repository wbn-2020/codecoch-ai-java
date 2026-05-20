package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
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

    private final FileStorageService fileStorageService;

    @GetMapping
    public Result<PageResult<FileInfoVO>> page(@ModelAttribute AdminFileQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(fileStorageService.pageAdminFiles(query));
    }

    @GetMapping("/{id}")
    public Result<FileInfoVO> detail(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(fileStorageService.getAdminFile(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return fileStorageService.adminDownload(id);
    }
}
