package com.codecoachai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.task.controller.NotificationController.NotificationVO;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.domain.entity.NotificationRead;
import com.codecoachai.task.mapper.NotificationMapper;
import com.codecoachai.task.mapper.NotificationReadMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final Long BROADCAST_USER_ID = 0L;

    private final NotificationMapper notificationMapper;
    private final NotificationReadMapper notificationReadMapper;

    public PageResult<NotificationVO> pageMyNotifications(Long userId, Long pageNo, Long pageSize, Integer readStatus,
                                                          String type) {
        Page<Notification> page = notificationMapper.selectUserNotificationPage(Page.of(pageNo, pageSize), userId,
                readStatus, type);
        Set<Long> readBroadcastIds = readBroadcastIds(userId, page.getRecords());
        List<NotificationVO> records = page.getRecords().stream()
                .map(notification -> toVO(notification, readBroadcastIds.contains(notification.getId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Long countUnread(Long userId) {
        return safeLong(notificationMapper.countUnreadForUser(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            return;
        }
        if (userId.equals(notification.getUserId())) {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getId, id)
                    .eq(Notification::getUserId, userId)
                    .set(Notification::getReadStatus, 1)
                    .set(Notification::getReadAt, LocalDateTime.now()));
            return;
        }
        if (BROADCAST_USER_ID.equals(notification.getUserId())) {
            insertBroadcastRead(userId, id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadStatus, 0)
                .set(Notification::getReadStatus, 1)
                .set(Notification::getReadAt, LocalDateTime.now()));
        notificationMapper.insertMissingBroadcastReads(userId);
    }

    private Set<Long> readBroadcastIds(Long userId, List<Notification> notifications) {
        List<Long> broadcastIds = notifications.stream()
                .filter(notification -> BROADCAST_USER_ID.equals(notification.getUserId()))
                .map(Notification::getId)
                .toList();
        if (broadcastIds.isEmpty()) {
            return Set.of();
        }
        List<NotificationRead> reads = notificationReadMapper.selectList(new LambdaQueryWrapper<NotificationRead>()
                .eq(NotificationRead::getUserId, userId)
                .in(NotificationRead::getNotificationId, broadcastIds));
        Set<Long> ids = new HashSet<>();
        reads.forEach(read -> ids.add(read.getNotificationId()));
        return ids;
    }

    private void insertBroadcastRead(Long userId, Long notificationId) {
        notificationMapper.upsertBroadcastRead(userId, notificationId);
    }

    private NotificationVO toVO(Notification notification, boolean broadcastRead) {
        NotificationVO vo = NotificationVO.from(notification);
        if (BROADCAST_USER_ID.equals(notification.getUserId())) {
            int effectiveReadStatus = broadcastRead ? 1 : 0;
            vo.setReadStatus(effectiveReadStatus);
            vo.setIsRead(effectiveReadStatus);
            if (!broadcastRead) {
                vo.setReadAt(null);
            }
        }
        return vo;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
