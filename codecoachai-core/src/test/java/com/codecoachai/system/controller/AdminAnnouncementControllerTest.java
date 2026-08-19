package com.codecoachai.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.system.domain.entity.SysAnnouncement;
import com.codecoachai.system.mapper.SysAnnouncementMapper;
import com.codecoachai.task.mapper.NotificationMapper;
import com.codecoachai.task.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnnouncementControllerTest {

    @Mock
    private SysAnnouncementMapper announcementMapper;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationService notificationService;

    private AdminAnnouncementController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAnnouncementController(
                announcementMapper,
                permissionGuard,
                operationConfirmationGuard,
                notificationMapper,
                notificationService);
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
    void updatePublishedAnnouncementReplacesTargetedAudienceNotifications() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "change announcement audience",
                "announcement-update-audience");
        dto.setTargetUsers("2002, 2001,2002");
        SysAnnouncement announcement = announcement();
        announcement.setStatus(1);
        announcement.setTargetUsers("1001,1002");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-update:12"),
                eq(true),
                eq(false),
                eq("change announcement audience"),
                eq("announcement-update-audience")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement);

        controller.update(12L, dto);

        assertEquals("2002,2001", announcement.getTargetUsers());
        InOrder inOrder = Mockito.inOrder(announcementMapper, notificationMapper, notificationService);
        inOrder.verify(announcementMapper).updateById(announcement);
        inOrder.verify(notificationMapper).delete(any());
        inOrder.verify(notificationService).createNotification(
                2002L, "ANNOUNCEMENT", "ANNOUNCEMENT", "12",
                "Maintenance Window", "System maintenance tonight at 22:00.");
        inOrder.verify(notificationService).createNotification(
                2001L, "ANNOUNCEMENT", "ANNOUNCEMENT", "12",
                "Maintenance Window", "System maintenance tonight at 22:00.");
        verify(notificationService, never()).createNotification(
                eq(1001L), any(), any(), any(), any(), any());
        verify(notificationService, never()).createNotification(
                eq(1002L), any(), any(), any(), any(), any());
    }

    @Test
    void updatePublishedAnnouncementCanChangeTargetedAudienceToBroadcast() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "broadcast announcement",
                "announcement-update-broadcast");
        dto.setTargetUsers("all");
        SysAnnouncement announcement = announcement();
        announcement.setStatus(1);
        announcement.setTargetUsers("1001");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-update:12"),
                eq(true),
                eq(false),
                eq("broadcast announcement"),
                eq("announcement-update-broadcast")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement);

        controller.update(12L, dto);

        assertEquals("ALL", announcement.getTargetUsers());
        verify(notificationMapper).delete(any());
        verify(notificationService).createNotification(
                0L, "ANNOUNCEMENT", "ANNOUNCEMENT", "12",
                "Maintenance Window", "System maintenance tonight at 22:00.");
    }

    @Test
    void updatePublishedAnnouncementCanChangeBroadcastToTargetedAudience() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "target announcement",
                "announcement-update-targeted");
        dto.setTargetUsers("3001,3002");
        SysAnnouncement announcement = announcement();
        announcement.setStatus(1);
        announcement.setTargetUsers("ALL");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-update:12"),
                eq(true),
                eq(false),
                eq("target announcement"),
                eq("announcement-update-targeted")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement);

        controller.update(12L, dto);

        verify(notificationMapper).delete(any());
        verify(notificationService).createNotification(
                3001L, "ANNOUNCEMENT", "ANNOUNCEMENT", "12",
                "Maintenance Window", "System maintenance tonight at 22:00.");
        verify(notificationService).createNotification(
                3002L, "ANNOUNCEMENT", "ANNOUNCEMENT", "12",
                "Maintenance Window", "System maintenance tonight at 22:00.");
        verify(notificationService, never()).createNotification(
                eq(0L), any(), any(), any(), any(), any());
    }

    @Test
    void updateDraftAudienceDoesNotCreateOrRemoveNotifications() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "update draft audience",
                "announcement-update-draft");
        dto.setTargetUsers("2001");
        SysAnnouncement announcement = announcement();
        announcement.setStatus(0);
        announcement.setTargetUsers("1001");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-update:12"),
                eq(true),
                eq(false),
                eq("update draft audience"),
                eq("announcement-update-draft")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement);

        controller.update(12L, dto);

        assertEquals("2001", announcement.getTargetUsers());
        verify(announcementMapper).updateById(announcement);
        verifyNoInteractions(notificationMapper, notificationService);
    }

    @Test
    void updatePublishedAnnouncementWithEquivalentAudienceDoesNotResetNotifications() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "update announcement content",
                "announcement-update-content");
        dto.setTargetUsers("1002, 1001,1002");
        SysAnnouncement announcement = announcement();
        announcement.setStatus(1);
        announcement.setTargetUsers("1001,1002");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-update:12"),
                eq(true),
                eq(false),
                eq("update announcement content"),
                eq("announcement-update-content")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement);

        controller.update(12L, dto);

        verify(announcementMapper).updateById(announcement);
        verifyNoInteractions(notificationMapper, notificationService);
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
        verify(notificationService).createNotification(
                0L,
                "ANNOUNCEMENT",
                "ANNOUNCEMENT",
                "12",
                "Maintenance Window",
                "System maintenance tonight at 22:00.");
    }

    @Test
    void publishCreatesNotificationsOnlyForConfiguredUsers() {
        AdminAnnouncementController.AdminOperationConfirmDTO dto = confirmDto("publish announcement",
                "announcement-publish-targeted");
        SysAnnouncement announcement = announcement();
        announcement.setTargetUsers("1001, 1002,1001");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-publish:12"),
                eq(true),
                eq(false),
                eq("publish announcement"),
                eq("announcement-publish-targeted")))
                .thenReturn("lock-key");
        when(announcementMapper.selectById(12L)).thenReturn(announcement);

        controller.publish(12L, dto);

        verify(notificationService).createNotification(
                1001L, "ANNOUNCEMENT", "ANNOUNCEMENT", "12",
                "Maintenance Window", "System maintenance tonight at 22:00.");
        verify(notificationService).createNotification(
                1002L, "ANNOUNCEMENT", "ANNOUNCEMENT", "12",
                "Maintenance Window", "System maintenance tonight at 22:00.");
        verify(notificationService, never()).createNotification(
                eq(0L), any(), any(), any(), any(), any());
    }

    @Test
    void listPublishedFiltersAnnouncementsByCurrentUser() {
        SysAnnouncement broadcast = announcement();
        broadcast.setId(11L);
        broadcast.setTargetUsers("ALL");
        SysAnnouncement currentUser = announcement();
        currentUser.setId(12L);
        currentUser.setTargetUsers("1001,1002");
        SysAnnouncement otherUser = announcement();
        otherUser.setId(13L);
        otherUser.setTargetUsers("2001");
        when(announcementMapper.selectList(any())).thenReturn(List.of(broadcast, currentUser, otherUser));

        var result = controller.listPublished();

        assertEquals(List.of(11L, 12L), result.getData().stream().map(SysAnnouncement::getId).toList());
    }

    @Test
    void createRejectsInvalidTargetUserScope() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "create announcement",
                "announcement-create-invalid-target");
        dto.setTargetUsers("1001,not-a-user");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-create:Maintenance Window"),
                eq(true),
                eq(false),
                eq("create announcement"),
                eq("announcement-create-invalid-target")))
                .thenReturn("lock-key");

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.create(dto));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(announcementMapper, never()).insert(any(SysAnnouncement.class));
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
        verify(notificationMapper).delete(any());
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

    @Test
    void announcementIdSerializesAsStringWithoutJavaScriptPrecisionLoss() throws Exception {
        SysAnnouncement announcement = announcement();
        announcement.setId(2082011113656750082L);

        String json = new ObjectMapper().writeValueAsString(announcement);

        assertTrue(json.contains("\"id\":\"2082011113656750082\""));
    }

    @Test
    void createReturnsAnnouncementIdAsString() {
        AdminAnnouncementController.AnnouncementSaveDTO dto = saveDto(false, "create announcement",
                "announcement-create-precise-id");
        when(operationConfirmationGuard.requireConfirmed(
                eq("announcement-create:Maintenance Window"),
                eq(true),
                eq(false),
                eq("create announcement"),
                eq("announcement-create-precise-id")))
                .thenReturn("lock-key");
        when(announcementMapper.insert(any(SysAnnouncement.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysAnnouncement.class).setId(2082011113656750082L);
            return 1;
        });

        var result = controller.create(dto);

        assertEquals("2082011113656750082", result.getData());
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
