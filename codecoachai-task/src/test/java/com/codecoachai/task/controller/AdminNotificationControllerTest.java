package com.codecoachai.task.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.task.mapper.NotificationMapper;
import com.codecoachai.task.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminNotificationControllerTest {

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private AdminNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminNotificationController(
                notificationMapper,
                notificationService,
                permissionGuard,
                operationConfirmationGuard,
                jdbcTemplate);
    }

    @Test
    void sendForwardsDryRunToConfirmationGuard() {
        AdminNotificationController.SendNotificationDTO dto = sendDto();
        when(operationConfirmationGuard.requireConfirmed(
                eq("notice-send:user:9"),
                eq(true),
                eq(false),
                eq("send notification"),
                eq("notice-send-1234")))
                .thenReturn("lock-key");

        controller.send(dto);

        verify(permissionGuard).require("admin:notice:write");
        verify(notificationService).notify(9L, "SYSTEM", null, null, "系统维护提醒", "今晚 22:00 维护。");
    }

    @Test
    void sendCompatForwardsDryRunToConfirmationGuard() {
        AdminNotificationController.SendNotificationDTO dto = sendDto();
        when(operationConfirmationGuard.requireConfirmed(
                eq("notice-send:user:9"),
                eq(true),
                eq(false),
                eq("send notification"),
                eq("notice-send-1234")))
                .thenReturn("lock-key");

        controller.sendCompat(dto);

        verify(permissionGuard).require("admin:notice:write");
        verify(notificationService).notify(9L, "SYSTEM", null, null, "系统维护提醒", "今晚 22:00 维护。");
    }

    @Test
    void broadcastForwardsDryRunToConfirmationGuard() {
        AdminNotificationController.BroadcastNotificationDTO dto = broadcastDto();
        when(operationConfirmationGuard.requireConfirmed(
                eq("notice-broadcast"),
                eq(true),
                eq(false),
                eq("broadcast notification"),
                eq("notice-broadcast-1234")))
                .thenReturn("lock-key");

        controller.broadcast(dto);

        verify(permissionGuard).require("admin:notice:write");
        verify(notificationService).notifySystem(0L, "系统维护提醒", "今晚 22:00 维护。");
    }

    @Test
    void deleteForwardsDryRunToConfirmationGuard() {
        AdminNotificationController.AdminOperationConfirmDTO dto = confirmDto("delete notification", "notice-delete-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("notice-delete:11"),
                eq(true),
                eq(false),
                eq("delete notification"),
                eq("notice-delete-1234")))
                .thenReturn("lock-key");

        controller.delete(11L, dto);

        verify(permissionGuard).require("admin:notice:write");
        verify(notificationMapper).deleteById(11L);
    }

    @Test
    void deleteReleasesIdempotencyLockWhenMapperFails() {
        AdminNotificationController.AdminOperationConfirmDTO dto = confirmDto("delete notification", "notice-delete-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("notice-delete:11"),
                eq(true),
                eq(false),
                eq("delete notification"),
                eq("notice-delete-1234")))
                .thenReturn("lock-key");
        when(notificationMapper.deleteById(11L)).thenThrow(new RuntimeException("database unavailable"));

        assertThrows(RuntimeException.class, () -> controller.delete(11L, dto));

        verify(operationConfirmationGuard).release("lock-key");
    }

    private static AdminNotificationController.SendNotificationDTO sendDto() {
        AdminNotificationController.SendNotificationDTO dto = new AdminNotificationController.SendNotificationDTO();
        dto.setTargetUserId(9L);
        dto.setTargetType("USER");
        dto.setType("SYSTEM");
        dto.setTitle("系统维护提醒");
        dto.setContent("今晚 22:00 维护。");
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("send notification");
        dto.setIdempotencyKey("notice-send-1234");
        return dto;
    }

    private static AdminNotificationController.BroadcastNotificationDTO broadcastDto() {
        AdminNotificationController.BroadcastNotificationDTO dto =
                new AdminNotificationController.BroadcastNotificationDTO();
        dto.setTitle("系统维护提醒");
        dto.setContent("今晚 22:00 维护。");
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("broadcast notification");
        dto.setIdempotencyKey("notice-broadcast-1234");
        return dto;
    }

    private static AdminNotificationController.AdminOperationConfirmDTO confirmDto(String reason, String idempotencyKey) {
        AdminNotificationController.AdminOperationConfirmDTO dto =
                new AdminNotificationController.AdminOperationConfirmDTO();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason(reason);
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }
}
