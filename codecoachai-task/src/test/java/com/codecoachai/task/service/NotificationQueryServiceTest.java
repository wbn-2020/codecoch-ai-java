package com.codecoachai.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.controller.NotificationController.NotificationVO;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.domain.vo.ReminderCandidateVO;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.ResumeFeignClient;
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
    @Mock
    private ResumeFeignClient resumeFeignClient;

    @Test
    void pageMyNotificationsAggregatesAgentAndResumeReminderCandidates() {
        Notification agentNotification = notification(101L, "AGENT_REMINDER", "AGENT_TASK", "run-42");
        agentNotification.setTitle("continue training");
        agentNotification.setContent("resume the pending task");
        Notification resumeNotification = notification(102L, "APPLICATION_FOLLOW_UP_REMINDER", "JOB_APPLICATION",
                "3001");
        resumeNotification.setTitle("follow up application");
        resumeNotification.setContent("application follow-up is due today");

        Page<Notification> page = Page.of(1, 20);
        page.setRecords(List.of(agentNotification, resumeNotification));
        page.setTotal(2);

        ReminderCandidateVO agentCandidate = candidate("AGENT_REMINDER", "AGENT_TASK", "run-42",
                "continue training", "resume the pending task",
                "/agent/tasks?bizType=agent.daily-plan.generate&bizId=run-42", "/agent/today",
                "today plan", LocalDate.now().minusDays(1));
        ReminderCandidateVO resumeCandidate = candidate("APPLICATION_FOLLOW_UP_REMINDER", "JOB_APPLICATION", "3001",
                "follow up application", "application follow-up is due today",
                "/applications?followUp=due-today", "/applications", "applications", LocalDate.now());

        when(aiFeignClient.listReminderCandidates(eq(9L), eq(LocalDate.now().minusDays(1))))
                .thenReturn(Result.success(List.of(agentCandidate)));
        when(resumeFeignClient.listApplicationReminderCandidates(eq(9L), eq(LocalDate.now())))
                .thenReturn(Result.success(List.of(resumeCandidate)));
        when(notificationMapper.selectUserNotificationPage(any(Page.class), eq(9L), eq(null), eq(null)))
                .thenReturn(page);

        NotificationQueryService service = new NotificationQueryService(
                notificationMapper,
                notificationReadMapper,
                notificationCommandService,
                aiFeignClient,
                resumeFeignClient);

        PageResult<NotificationVO> result = service.pageMyNotifications(9L, 1L, 20L, null, null);

        assertEquals(2, result.getRecords().size());
        NotificationVO agentRecord = result.getRecords().get(0);
        assertEquals("/agent/tasks?bizType=agent.daily-plan.generate&bizId=run-42", agentRecord.getActionUrl());
        assertEquals("/agent/today", agentRecord.getFallbackPath());
        assertEquals("today plan", agentRecord.getFallbackLabel());
        assertEquals(LocalDate.now().minusDays(1), agentRecord.getPlanDate());
        assertEquals("AGENT_TASK", agentRecord.getRelatedType());
        assertEquals("run-42", agentRecord.getRelatedId());

        NotificationVO resumeRecord = result.getRecords().get(1);
        assertEquals("/applications?followUp=due-today", resumeRecord.getActionUrl());
        assertEquals("/applications", resumeRecord.getFallbackPath());
        assertEquals("applications", resumeRecord.getFallbackLabel());
        assertEquals(LocalDate.now(), resumeRecord.getPlanDate());
        assertEquals("JOB_APPLICATION", resumeRecord.getRelatedType());
        assertEquals("3001", resumeRecord.getRelatedId());

        verify(notificationCommandService).ensureDailyReminder(
                9L,
                "AGENT_REMINDER",
                "continue training",
                "resume the pending task",
                "AGENT_TASK",
                "run-42");
        verify(notificationCommandService).ensureDailyReminder(
                9L,
                "APPLICATION_FOLLOW_UP_REMINDER",
                "follow up application",
                "application follow-up is due today",
                "JOB_APPLICATION",
                "3001");
    }

    @Test
    void countUnreadDegradesWhenResumeReminderCandidatesFail() {
        when(aiFeignClient.listReminderCandidates(eq(9L), eq(LocalDate.now().minusDays(1))))
                .thenReturn(Result.success(List.of()));
        doThrow(new IllegalStateException("resume unavailable"))
                .when(resumeFeignClient).listApplicationReminderCandidates(eq(9L), eq(LocalDate.now()));
        when(notificationMapper.countUnreadForUser(9L)).thenReturn(7L);

        NotificationQueryService service = new NotificationQueryService(
                notificationMapper,
                notificationReadMapper,
                notificationCommandService,
                aiFeignClient,
                resumeFeignClient);

        assertEquals(7L, service.countUnread(9L));
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

    private Notification notification(Long id, String type, String bizType, String bizId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(9L);
        notification.setType(type);
        notification.setTitle("reminder");
        notification.setContent("body");
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setReadStatus(0);
        return notification;
    }

    private ReminderCandidateVO candidate(String type, String bizType, String bizId, String title, String content,
                                          String actionUrl, String fallbackPath, String fallbackLabel,
                                          LocalDate planDate) {
        ReminderCandidateVO candidate = new ReminderCandidateVO();
        candidate.setType(type);
        candidate.setBizType(bizType);
        candidate.setBizId(bizId);
        candidate.setTitle(title);
        candidate.setContent(content);
        candidate.setActionUrl(actionUrl);
        candidate.setFallbackPath(fallbackPath);
        candidate.setFallbackLabel(fallbackLabel);
        candidate.setPlanDate(planDate);
        return candidate;
    }
}
