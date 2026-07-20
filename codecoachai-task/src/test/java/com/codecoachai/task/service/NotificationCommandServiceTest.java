package com.codecoachai.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.task.controller.NotificationController.NotificationVO;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMapper notificationMapper;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(Notification.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
        }
    }

    @Test
    void resolveByBizUpdatesOnlyUserOwnedUnresolvedNotifications() {
        NotificationCommandService service = new NotificationCommandService(notificationService, notificationMapper);
        when(notificationMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        int updated = service.resolveByBiz(9L, "APPLICATION_FOLLOW_UP_REMINDER", "JOB_APPLICATION",
                "3001", "JOB_APPLICATION_EVENT:88");

        assertEquals(1, updated);
        verify(notificationMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void resolveByBizIgnoresBroadcastAndInvalidTargets() {
        NotificationCommandService service = new NotificationCommandService(notificationService, notificationMapper);

        assertEquals(0, service.resolveByBiz(0L, "SYSTEM", "NOTICE", "1", "done"));
        assertEquals(0, service.resolveByBiz(null, "SYSTEM", "NOTICE", "1", "done"));

        verify(notificationMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void ensureDailyReminderUsesAtomicBusinessDateInsert() {
        NotificationCommandService service = new NotificationCommandService(notificationService, notificationMapper);
        LocalDate reminderDate = LocalDate.of(2026, 7, 20);

        service.ensureDailyReminder(
                9L,
                "CALENDAR_REMINDER",
                "今天的求职日程",
                "面试即将开始",
                "CAREER_CALENDAR_EVENT",
                "501",
                reminderDate);

        verify(notificationMapper).insertDailyReminderIfAbsent(
                eq(9L),
                eq("CALENDAR_REMINDER"),
                eq("今天的求职日程"),
                eq("面试即将开始"),
                eq("CAREER_CALENDAR_EVENT"),
                eq("501"),
                eq(reminderDate),
                any(LocalDateTime.class));
        verify(notificationService, never()).createNotification(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureDailyReminderFallsBackForLegacySchema() {
        NotificationCommandService service = new NotificationCommandService(notificationService, notificationMapper);
        when(notificationMapper.insertDailyReminderIfAbsent(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unknown reminder_date"));
        when(notificationMapper.selectList(any())).thenReturn(List.of());

        service.ensureDailyReminder(
                9L,
                "CALENDAR_REMINDER",
                "今天的求职日程",
                "面试即将开始",
                "CAREER_CALENDAR_EVENT",
                "501",
                LocalDate.of(2026, 7, 20));

        verify(notificationService).createNotification(
                9L,
                "CALENDAR_REMINDER",
                "CAREER_CALENDAR_EVENT",
                "501",
                "今天的求职日程",
                "面试即将开始");
    }

    @Test
    void notificationVoFromIncludesResolvedStatusWithoutChangingReadStatus() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUserId(9L);
        notification.setType("APPLICATION_FOLLOW_UP_REMINDER");
        notification.setBizType("JOB_APPLICATION");
        notification.setBizId("3001");
        notification.setReadStatus(0);
        notification.setResolvedStatus(1);
        notification.setResolvedReason("JOB_APPLICATION_EVENT:88");

        NotificationVO vo = NotificationVO.from(notification);

        assertEquals(0, vo.getReadStatus());
        assertEquals(1, vo.getResolvedStatus());
        assertEquals("JOB_APPLICATION_EVENT:88", vo.getResolvedReason());
    }
}
