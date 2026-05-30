package com.codecoachai.task.service;

import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        notificationMapper.insert(notification);
        return notification.getId();
    }
}
