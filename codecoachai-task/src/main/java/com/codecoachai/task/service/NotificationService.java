package com.codecoachai.task.service;

import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务。
 * 异步消费者在任务完成/失败后调用此服务推送通知给用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

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
        send(userId, "TASK_FAILED", bizType, bizId, title, content);
    }

    /**
     * 发送系统通知。
     */
    public void notifySystem(Long userId, String title, String content) {
        send(userId, "SYSTEM", null, null, title, content);
    }

    private void send(Long userId, String type, String bizType, String bizId, String title, String content) {
        if (userId == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setBizType(bizType);
            n.setBizId(bizId);
            n.setReadStatus(0);
            notificationMapper.insert(n);
            log.debug("通知已发送 userId={} type={} title={}", userId, type, title);
        } catch (Exception ex) {
            log.warn("发送通知失败 userId={} type={}", userId, type, ex);
        }
    }
}
