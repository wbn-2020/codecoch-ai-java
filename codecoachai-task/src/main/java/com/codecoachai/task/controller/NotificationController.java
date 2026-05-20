package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端通知接口（由 task-service 提供，gateway 路由过来）。
 */
@Tag(name = "通知-用户端")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationMapper notificationMapper;

    @Operation(summary = "我的通知列表")
    @GetMapping
    public Result<PageResult<NotificationVO>> myNotifications(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) Integer readStatus) {
        Long userId = SecurityAssert.requireLoginUserId();
        Page<Notification> page = notificationMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(readStatus != null, Notification::getReadStatus, readStatus)
                        .orderByDesc(Notification::getCreatedAt));
        List<NotificationVO> records = page.getRecords().stream()
                .map(NotificationVO::from)
                .toList();
        return Result.success(PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = SecurityAssert.requireLoginUserId();
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getReadStatus, 0));
        return Result.success(count);
    }

    @Operation(summary = "标记单条已读")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, id)
                        .eq(Notification::getUserId, SecurityAssert.requireLoginUserId())
                        .set(Notification::getReadStatus, 1)
                        .set(Notification::getReadAt, LocalDateTime.now()));
        return Result.success();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = SecurityAssert.requireLoginUserId();
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getReadStatus, 0)
                        .set(Notification::getReadStatus, 1)
                        .set(Notification::getReadAt, LocalDateTime.now()));
        return Result.success();
    }

    @Data
    public static class NotificationVO {
        private Long id;
        private Long userId;
        private String type;
        private String title;
        private String content;
        private String bizType;
        private String bizId;
        private String relatedType;
        private String relatedId;
        private Integer readStatus;
        private Integer isRead;
        private LocalDateTime readAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        static NotificationVO from(Notification notification) {
            NotificationVO vo = new NotificationVO();
            vo.setId(notification.getId());
            vo.setUserId(notification.getUserId());
            vo.setType(notification.getType());
            vo.setTitle(notification.getTitle());
            vo.setContent(notification.getContent());
            vo.setBizType(notification.getBizType());
            vo.setBizId(notification.getBizId());
            vo.setRelatedType(notification.getBizType());
            vo.setRelatedId(notification.getBizId());
            vo.setReadStatus(notification.getReadStatus());
            vo.setIsRead(notification.getReadStatus());
            vo.setReadAt(notification.getReadAt());
            vo.setCreatedAt(notification.getCreatedAt());
            vo.setUpdatedAt(notification.getUpdatedAt());
            return vo;
        }
    }
}
