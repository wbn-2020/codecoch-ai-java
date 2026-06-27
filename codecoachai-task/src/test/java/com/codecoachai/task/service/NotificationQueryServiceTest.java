package com.codecoachai.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.controller.NotificationController.NotificationVO;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.mapper.NotificationMapper;
import com.codecoachai.task.mapper.NotificationReadMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationReadMapper notificationReadMapper;
    @Mock
    private NotificationCommandService notificationCommandService;
    @Mock
    private AiFeignClient aiFeignClient;

    @Test
    void pageMyNotificationsAddsReminderDeepLinkContractFields() {
        Notification notification = new Notification();
        notification.setId(101L);
        notification.setUserId(9L);
        notification.setType("AGENT_REMINDER");
        notification.setTitle("continue training");
        notification.setContent("resume the pending task");
        notification.setBizType("AGENT_TASK");
        notification.setBizId("run-42");
        notification.setReadStatus(0);

        Page<Notification> page = Page.of(1, 20);
        page.setRecords(List.of(notification));
        page.setTotal(1);

        AiFeignClient.AgentReminderCandidateVO candidate = new AiFeignClient.AgentReminderCandidateVO();
        candidate.setType("AGENT_REMINDER");
        candidate.setBizType("AGENT_TASK");
        candidate.setBizId("run-42");
        candidate.setTitle("continue training");
        candidate.setContent("resume the pending task");
        candidate.setActionUrl("/agent/tasks?bizType=agent.daily-plan.generate&bizId=run-42");
        candidate.setFallbackPath("/agent/today");
        candidate.setFallbackLabel("today plan");
        candidate.setPlanDate(LocalDate.of(2026, 6, 27));

        when(aiFeignClient.listReminderCandidates(eq(9L), any(LocalDate.class)))
                .thenReturn(Result.success(List.of(candidate)));
        when(notificationMapper.selectUserNotificationPage(any(Page.class), eq(9L), eq(null), eq(null)))
                .thenReturn(page);

        NotificationQueryService service = new NotificationQueryService(
                notificationMapper,
                notificationReadMapper,
                notificationCommandService,
                aiFeignClient);

        PageResult<NotificationVO> result = service.pageMyNotifications(9L, 1L, 20L, null, null);

        assertEquals(1, result.getRecords().size());
        NotificationVO record = result.getRecords().get(0);
        assertEquals("/agent/tasks?bizType=agent.daily-plan.generate&bizId=run-42", record.getActionUrl());
        assertEquals("/agent/today", record.getFallbackPath());
        assertEquals("today plan", record.getFallbackLabel());
        assertEquals(LocalDate.of(2026, 6, 27), record.getPlanDate());
        assertEquals("AGENT_TASK", record.getRelatedType());
        assertEquals("run-42", record.getRelatedId());

        verify(notificationCommandService).ensureDailyReminder(
                9L,
                "AGENT_REMINDER",
                "continue training",
                "resume the pending task",
                "AGENT_TASK",
                "run-42");
    }

    @Test
    void notificationVoFromMirrorsReminderContractFields() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUserId(9L);
        notification.setType("AGENT_REMINDER");
        notification.setTitle("reminder");
        notification.setContent("body");
        notification.setBizType("AGENT_TASK");
        notification.setBizId("biz-1");
        notification.setReadStatus(0);

        NotificationVO vo = NotificationVO.from(notification);
        vo.setActionUrl("/agent/today");
        vo.setFallbackPath("/dashboard");
        vo.setFallbackLabel("dashboard");
        vo.setPlanDate(LocalDate.of(2026, 6, 28));

        assertNotNull(vo);
        assertEquals("/agent/today", vo.getActionUrl());
        assertEquals("/dashboard", vo.getFallbackPath());
        assertEquals("dashboard", vo.getFallbackLabel());
        assertEquals(LocalDate.of(2026, 6, 28), vo.getPlanDate());
    }
}
