package com.codecoachai.task.service;

import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 站内通知服务。
 * 异步消费者在任务完成/失败后调用此服务推送通知给用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 发送任务完成通知。
     */
    public void notifyTaskDone(Long userId, String bizType, String bizId, String title, String content) {
        send(userId, "TASK_DONE", bizType, bizId, title, content);
    }

    /**
     * 发送任务失败通知。
     */
    public void notifyTaskFailed(Long userId, String bizType, String bizId, String title, String content) {
        send(userId, "TASK_FAILED", bizType, bizId, title, safeTaskFailedContent(content));
    }

    /**
     * 发送系统通知。
     */
    public void notifySystem(Long userId, String title, String content) {
        send(userId, "SYSTEM", null, null, title, content);
    }

    /**
     * 发送指定类型的站内通知。
     */
    public void notify(Long userId, String type, String bizType, String bizId, String title, String content) {
        send(userId, type, bizType, bizId, title, content);
    }

    public Notification createNotification(Long userId, String type, String bizType, String bizId,
                                           String title, String content) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(StringUtils.hasText(type) ? type.trim() : "SYSTEM");
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setReadStatus(0);
        notification.setSendStatus("SUCCESS");
        notification.setSentAt(LocalDateTime.now());
        return saveNotification(notification);
    }

    private void send(Long userId, String type, String bizType, String bizId, String title, String content) {
        String normalizedType = StringUtils.hasText(type) ? type.trim() : "SYSTEM";
        LocalDateTime now = LocalDateTime.now();
        if (userId == null) {
            persistFailed(0L, normalizedType, bizType, bizId, title, content, "Missing notification target userId", now);
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(normalizedType);
            n.setTitle(title);
            n.setContent(content);
            n.setBizType(bizType);
            n.setBizId(bizId);
            n.setReadStatus(0);
            n.setSendStatus("SUCCESS");
            n.setSentAt(now);
            saveNotification(n);
            log.debug("通知已发送 userId={} type={} title={}", userId, normalizedType, title);
        } catch (Exception ex) {
            log.warn("发送通知失败 userId={} type={}", userId, normalizedType, ex);
        }
    }

    private String safeTaskFailedContent(String content) {
        return "任务处理失败，请稍后重试。";
    }

    private void persistFailed(Long userId, String type, String bizType, String bizId, String title, String content,
                               String reason, LocalDateTime sentAt) {
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setBizType(bizType);
            n.setBizId(bizId);
            n.setReadStatus(0);
            n.setSendStatus("FAILED");
            n.setSendError(truncate(reason, 1000));
            n.setSentAt(sentAt);
            saveNotification(n);
        } catch (Exception ex) {
            log.warn("通知失败状态写入失败 userId={} type={} reason={}", userId, type, reason, ex);
        }
    }

    public Notification saveNotification(Notification notification) {
        try {
            notificationMapper.insert(notification);
            return notification;
        } catch (DataAccessException ex) {
            return persistNotificationWithExistingColumns(notification, ex);
        }
    }

    private Notification persistNotificationWithExistingColumns(Notification notification, DataAccessException original) {
        try {
            List<String> columns = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            addColumn(columns, args, "user_id", notification.getUserId());
            addColumn(columns, args, "type", notification.getType());
            addColumn(columns, args, "title", notification.getTitle());
            addColumn(columns, args, "content", notification.getContent());
            addColumnIfExists(columns, args, "biz_type", notification.getBizType());
            addColumnIfExists(columns, args, "biz_id", notification.getBizId());
            addColumnIfExists(columns, args, "read_status", notification.getReadStatus() == null ? 0 : notification.getReadStatus());
            addColumnIfExists(columns, args, "read_at", notification.getReadAt());
            addColumnIfExists(columns, args, "resolved_status",
                    notification.getResolvedStatus() == null ? 0 : notification.getResolvedStatus());
            addColumnIfExists(columns, args, "resolved_at", notification.getResolvedAt());
            addColumnIfExists(columns, args, "resolved_reason", notification.getResolvedReason());
            addColumnIfExists(columns, args, "send_status", notification.getSendStatus());
            addColumnIfExists(columns, args, "send_error", notification.getSendError());
            addColumnIfExists(columns, args, "sent_at", notification.getSentAt());
            addColumnIfExists(columns, args, "created_at", firstTime(notification.getCreatedAt(), notification.getSentAt(), LocalDateTime.now()));
            addColumnIfExists(columns, args, "updated_at", firstTime(notification.getUpdatedAt(), notification.getSentAt(), LocalDateTime.now()));
            addColumnIfExists(columns, args, "deleted", notification.getDeleted() == null ? 0 : notification.getDeleted());

            String placeholders = String.join(", ", columns.stream().map(column -> "?").toList());
            String sql = "INSERT INTO notification (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < args.size(); i++) {
                    ps.setObject(i + 1, args.get(i));
                }
                return ps;
            }, keyHolder);
            Number generatedId = keyHolder.getKey();
            if (generatedId != null && notification.getId() == null) {
                notification.setId(generatedId.longValue());
            }
            log.warn("通知按旧表兼容写入成功，原始插入失败原因：{}", original.getMessage());
            return notification;
        } catch (Exception ex) {
            log.warn("通知旧表兼容写入失败 userId={} type={}", notification.getUserId(), notification.getType(), ex);
            original.addSuppressed(ex);
            throw original;
        }
    }

    private void addColumn(List<String> columns, List<Object> args, String column, Object value) {
        columns.add(column);
        args.add(value);
    }

    private void addColumnIfExists(List<String> columns, List<Object> args, String column, Object value) {
        if (!columnExists(column)) {
            return;
        }
        addColumn(columns, args, column, value);
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'notification'
                  AND column_name = ?
                """, Integer.class, columnName);
        return count != null && count > 0;
    }

    private LocalDateTime firstTime(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return LocalDateTime.now();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
