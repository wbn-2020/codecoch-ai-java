package com.codecoachai.file.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.file.domain.dto.AdminFileDownloadAccessDTO;
import com.codecoachai.file.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class AdminFileControllerTest {

    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminFileController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminFileController(fileStorageService, permissionGuard, operationConfirmationGuard);
    }

    @Test
    void downloadConfirmedUsesSharedConfirmationGuardBeforeReturningFile() {
        AdminFileDownloadAccessDTO dto = downloadDto();
        Resource body = new ByteArrayResource(new byte[] {1, 2, 3});
        when(operationConfirmationGuard.requireConfirmed(
                eq("admin-file-download:7"),
                eq(true),
                eq(false),
                eq("audit file download"),
                eq("file-download-1234"))).thenReturn("file-download-lock");
        when(fileStorageService.adminDownload(7L)).thenReturn(ResponseEntity.ok(body));

        ResponseEntity<Resource> result = controller.downloadConfirmed(7L, dto);

        assertSame(body, result.getBody());
        verify(permissionGuard).require("admin:file:download");
        verify(fileStorageService).adminDownload(7L);
    }

    @Test
    void downloadConfirmedReleasesConfirmationLockWhenDownloadFails() {
        AdminFileDownloadAccessDTO dto = downloadDto();
        when(operationConfirmationGuard.requireConfirmed(
                eq("admin-file-download:7"),
                eq(true),
                eq(false),
                eq("audit file download"),
                eq("file-download-1234"))).thenReturn("file-download-lock");
        doThrow(new IllegalStateException("download failed"))
                .when(fileStorageService).adminDownload(7L);

        assertThrows(IllegalStateException.class, () -> controller.downloadConfirmed(7L, dto));

        verify(operationConfirmationGuard).release("file-download-lock");
    }

    private static AdminFileDownloadAccessDTO downloadDto() {
        AdminFileDownloadAccessDTO dto = new AdminFileDownloadAccessDTO();
        dto.setAccessReason("legacy audit file download");
        dto.setConfirmSensitiveAccess(true);
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("audit file download");
        dto.setIdempotencyKey("file-download-1234");
        return dto;
    }
}
