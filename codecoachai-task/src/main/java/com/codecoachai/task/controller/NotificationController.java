package com.codecoachai.task.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知-用户端")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @Operation(summary = "我的通知列表")
    @GetMapping
    public Result<PageResult<NotificationVO>> myNotifications(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) Integer readStatus,
            @RequestParam(required = false) String type) {
        Long userId = SecurityAssert.requireLoginUserId();
        long safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        long safePageSize = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        return Result.success(notificationQueryService.pageMyNotifications(userId, safePageNo, safePageSize,
                readStatus, type));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(notificationQueryService.countUnread(userId));
    }

    @Operation(summary = "标记单条已读")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationQueryService.markRead(SecurityAssert.requireLoginUserId(), id);
        return Result.success();
    }

    @Operation(summary = "标记单条已读（PUT 兼容）")
    @PutMapping("/{id}/read")
    public Result<Void> markReadCompat(@PathVariable Long id) {
        return markRead(id);
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationQueryService.markAllRead(SecurityAssert.requireLoginUserId());
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
        private Integer resolvedStatus;
        private LocalDateTime resolvedAt;
        private String resolvedReason;
        private String sendStatus;
        private String sendError;
        private LocalDateTime sentAt;
        private String actionUrl;
        private String fallbackPath;
        private String fallbackLabel;
        private LocalDate planDate;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static NotificationVO from(Notification notification) {
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
            vo.setResolvedStatus(notification.getResolvedStatus());
            vo.setResolvedAt(notification.getResolvedAt());
            vo.setResolvedReason(notification.getResolvedReason());
            vo.setSendStatus(notification.getSendStatus());
            vo.setSendError(notification.getSendError());
            vo.setSentAt(notification.getSentAt());
            vo.setCreatedAt(notification.getCreatedAt());
            vo.setUpdatedAt(notification.getUpdatedAt());
            return vo;
        }
    }
}
