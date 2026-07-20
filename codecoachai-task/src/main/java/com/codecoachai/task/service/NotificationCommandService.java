package com.codecoachai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 站内通知写入服务。作为各业务模块发送通知的统一入口（通过内部接口调用）。
 * 支持四类关键事件：TASK_DUE / INTERVIEW_REPORT_READY / KNOWLEDGE_INDEX_REBUILT / NEW_DUPLICATE_PENDING。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private static final Long BROADCAST_USER_ID = 0L;

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    /**
     * 创建一条用户通知。userId 为空时落为系统公告（0=所有人）。
     *
     * @return 通知 id
     */
    public Long create(Long userId, String type, String title, String content, String bizType, String bizId) {
        Notification notification = new Notification();
        notification.setUserId(userId == null ? BROADCAST_USER_ID : userId);
        notification.setType(StringUtils.hasText(type) ? type : "SYSTEM");
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setReadStatus(0);
        notification.setSendStatus("SUCCESS");
        notification.setSentAt(LocalDateTime.now());
        notificationService.saveNotification(notification);
        return notification.getId();
    }

    public void ensureDailyReminder(Long userId, String type, String title, String content, String bizType, String bizId) {
        ensureDailyReminder(userId, type, title, content, bizType, bizId, LocalDate.now());
    }

    public void ensureDailyReminder(Long userId, String type, String title, String content,
                                    String bizType, String bizId, LocalDate reminderDate) {
        if (userId == null || !StringUtils.hasText(type) || !StringUtils.hasText(title)
                || !StringUtils.hasText(content) || !StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return;
        }
        String normalizedType = type.trim();
        String normalizedBizType = bizType.trim();
        String normalizedBizId = bizId.trim();
        LocalDate effectiveReminderDate = reminderDate == null ? LocalDate.now() : reminderDate;
        LocalDateTime sentAt = LocalDateTime.now();
        try {
            notificationMapper.insertDailyReminderIfAbsent(
                    userId,
                    normalizedType,
                    title,
                    content,
                    normalizedBizType,
                    normalizedBizId,
                    effectiveReminderDate,
                    sentAt);
            return;
        } catch (DataAccessException ex) {
            log.warn("每日提醒原子写入失败，回退旧表兼容逻辑 userId={} type={} bizType={} bizId={}",
                    userId, normalizedType, normalizedBizType, normalizedBizId, ex);
        }

        ensureDailyReminderWithLegacySchema(
                userId, normalizedType, title, content, normalizedBizType, normalizedBizId);
    }

    private void ensureDailyReminderWithLegacySchema(Long userId, String type, String title, String content,
                                                     String bizType, String bizId) {
        LocalDateTime start = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);
        List<Notification> existing = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, type)
                .eq(Notification::getBizType, bizType)
                .eq(Notification::getBizId, bizId)
                .ge(Notification::getCreatedAt, start)
                .le(Notification::getCreatedAt, end)
                .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            return;
        }
        notificationService.createNotification(userId, type, bizType, bizId, title, content);
    }

    public int resolveByBiz(Long userId, String type, String bizType, String bizId, String reason) {
        if (userId == null || userId <= BROADCAST_USER_ID || !StringUtils.hasText(type)
                || !StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return 0;
        }
        return notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, type.trim())
                .eq(Notification::getBizType, bizType.trim())
                .eq(Notification::getBizId, bizId.trim())
                .eq(Notification::getDeleted, 0)
                .and(wrapper -> wrapper
                        .isNull(Notification::getResolvedStatus)
                        .or()
                        .eq(Notification::getResolvedStatus, 0))
                .set(Notification::getResolvedStatus, 1)
                .set(Notification::getResolvedAt, LocalDateTime.now())
                .set(Notification::getResolvedReason, truncate(reason, 64)));
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
