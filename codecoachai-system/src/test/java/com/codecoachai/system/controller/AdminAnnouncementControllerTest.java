package com.codecoachai.system.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.system.domain.entity.SysAnnouncement;
import com.codecoachai.system.mapper.SysAnnouncementMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnnouncementControllerTest {

    @Mock
    private SysAnnouncementMapper announcementMapper;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminAnnouncementController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAnnouncementController(
                announcementMapper,
                permissionGuard,
                operationConfirmationGuard);
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(1001L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createForwardsDryRunToConfirmationGuard() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "create announcement",
                "announcement-create-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-create:Maintenance Window"),
                eq(true),
                eq(false),
                eq("create announcement"),
                eq("announcement-create-1234")))
                .thenReturn("lock-key");

        controller.create(dto);

        verify(permissionGuard).require("admin:announcement:write");
        verify(announcementMapper).insert(any(SysAnnouncement.class));
    }

    @Test
    void updateForwardsDryRunToConfirmationGuard() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "update announcement",
                "announcement-update-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-update:12"),
                eq(true),
                eq(false),
                eq("update announcement"),
                eq("announcement-update-1234")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement());

        controller.update(12L, dto);

        verify(permissionGuard).require("admin:announcement:write");
        verify(announcementMapper).updateById(any(SysAnnouncement.class));
    }

    @Test
    void publishForwardsDryRunToConfirmationGuard() {
        AdminAnnouncementController.AdminOperationConfirmDTO dto = confirmDto("publish announcement",
                "announcement-publish-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-publish:12"),
                eq(true),
                eq(false),
                eq("publish announcement"),
                eq("announcement-publish-1234")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement());

        controller.publish(12L, dto);

        verify(permissionGuard).require("admin:announcement:publish");
        verify(announcementMapper).updateById(any(SysAnnouncement.class));
    }

    @Test
    void offlineForwardsDryRunToConfirmationGuard() {
        AdminAnnouncementController.AdminOperationConfirmDTO dto = confirmDto("offline announcement",
                "announcement-offline-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-offline:12"),
                eq(true),
                eq(false),
                eq("offline announcement"),
                eq("announcement-offline-1234")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement());

        controller.offline(12L, dto);

        verify(permissionGuard).require("admin:announcement:publish");
        verify(announcementMapper).updateById(any(SysAnnouncement.class));
    }

    @Test
    void deleteForwardsDryRunToConfirmationGuard() {
        AdminAnnouncementController.AdminOperationConfirmDTO dto = confirmDto("delete announcement",
                "announcement-delete-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-delete:12"),
                eq(true),
                eq(false),
                eq("delete announcement"),
                eq("announcement-delete-1234")))
                .thenReturn("lock-key");

        controller.delete(12L, dto);

        verify(permissionGuard).require("admin:announcement:write");
        verify(announcementMapper).deleteById(12L);
    }

    @Test
    void createDoesNotInsertWhenGuardRejectsDryRun() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(true, "preview announcement",
                "announcement-create-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-create:Maintenance Window"),
                eq(true),
                eq(true),
                eq("preview announcement"),
                eq("announcement-create-1234")))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dryRun requests are blocked"));

        assertThrows(BusinessException.class, () -> controller.create(dto));

        verify(permissionGuard).require("admin:announcement:write");
        verify(announcementMapper, never()).insert(any(SysAnnouncement.class));
    }

    @Test
    void updateReleasesIdempotencyLockWhenMapperFails() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "update announcement",
                "announcement-update-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-update:12"),
                eq(true),
                eq(false),
                eq("update announcement"),
                eq("announcement-update-1234")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement());
        when(announcementMapper.updateById(any(SysAnnouncement.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThrows(RuntimeException.class, () -> controller.update(12L, dto));

        verify(operationConfirmationGuard).release("lock-key");
    }

    private static AdminAnnouncementController.AnnouncementSaveDTO saveDto(
            Boolean dryRun, String reason, String idempotencyKey) {
        AdminAnnouncementController.AnnouncementSaveDTO dto =
                new AdminAnnouncementController.AnnouncementSaveDTO();
        dto.setTitle("Maintenance Window");
        dto.setContent("System maintenance tonight at 22:00.");
        dto.setType("NORMAL");
        dto.setTargetUsers("ALL");
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason(reason);
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static AdminAnnouncementController.AdminOperationConfirmDTO confirmDto(
            String reason, String idempotencyKey) {
        AdminAnnouncementController.AdminOperationConfirmDTO dto =
                new AdminAnnouncementController.AdminOperationConfirmDTO();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason(reason);
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static SysAnnouncement announcement() {
        SysAnnouncement announcement = new SysAnnouncement();
        announcement.setId(12L);
        announcement.setTitle("Maintenance Window");
        announcement.setContent("System maintenance tonight at 22:00.");
        announcement.setType("NORMAL");
        announcement.setStatus(0);
        return announcement;
    }
}
