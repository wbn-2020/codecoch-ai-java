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
import org.springframework.web.bind.annotation.PutMapping;
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
        // 用户端列表需要合并管理端广播通知：userId=0 是历史约定的全站公告，
        // 但广播没有个人已读状态，因此已读筛选仍只作用在当前用户的专属通知上。
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .and(query -> query.eq(Notification::getUserId, userId).or().eq(Notification::getUserId, 0L));
        if (readStatus != null) {
            wrapper.eq(Notification::getUserId, userId)
                    .eq(Notification::getReadStatus, readStatus);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);
        Page<Notification> page = notificationMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
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
        // 更新条件同时限定 id 和当前用户，避免用户通过枚举通知 ID 修改他人通知状态。
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, id)
                        .eq(Notification::getUserId, SecurityAssert.requireLoginUserId())
                        .set(Notification::getReadStatus, 1)
                        .set(Notification::getReadAt, LocalDateTime.now()));
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
        Long userId = SecurityAssert.requireLoginUserId();
        // 只批量更新当前用户未读通知，已经读过的记录不重复刷新 readAt。
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
