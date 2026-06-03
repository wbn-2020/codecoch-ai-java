package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import com.codecoachai.task.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端通知管理 Controller。
 * 管理员可以：查看所有通知、发送系统通知给指定用户或全体用户、删除通知。
 */
@Tag(name = "通知管理-后台")
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private static final String PERM_NOTICE_LIST = "admin:notice:list";
    private static final String PERM_NOTICE_WRITE = "admin:notice:write";

    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;
    private final AdminPermissionGuard adminPermissionGuard;

    @Operation(summary = "分页查询所有通知")
    @GetMapping
    public Result<PageResult<Notification>> page(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer readStatus) {
        adminPermissionGuard.require(PERM_NOTICE_LIST);
        // status 是早期前端筛选字段，readStatus 是新字段；两者都保留，避免旧管理页筛选失效。
        Integer resolvedReadStatus = readStatus != null ? readStatus : status;
        Page<Notification> page = notificationMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(Notification::getTitle, keyword)
                                .or().like(Notification::getContent, keyword)
                                .or().like(Notification::getType, keyword)
                                .or().like(Notification::getBizType, keyword)
                                .or().like(Notification::getBizId, keyword))
                        .eq(userId != null, Notification::getUserId, userId)
                        .eq(StringUtils.hasText(type), Notification::getType, type)
                        .eq(resolvedReadStatus != null, Notification::getReadStatus, resolvedReadStatus)
                        .orderByDesc(Notification::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "发送系统通知给指定用户")
    @OperationLog(module = "notification", action = "SEND_NOTICE", description = "发送系统通知", logArgs = false)
    @PostMapping("/send")
    public Result<Void> send(@Valid @RequestBody SendNotificationDTO dto) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        doSend(dto);
        return Result.success();
    }

    @OperationLog(module = "notification", action = "SEND_NOTICE_COMPAT", description = "兼容入口发送系统通知", logArgs = false)
    @PostMapping
    public Result<Void> sendCompat(@Valid @RequestBody SendNotificationDTO dto) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        // 兼容旧管理端直接 POST /admin/notifications 的发送入口，实际发送规则统一收口到 doSend。
        doSend(dto);
        return Result.success();
    }

    private void doSend(SendNotificationDTO dto) {
        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            for (Long uid : dto.getUserIds()) {
                notificationService.notifySystem(uid, dto.getTitle(), dto.getContent());
            }
        } else if (dto.getTargetUserId() != null) {
            notificationService.notifySystem(dto.getTargetUserId(), dto.getTitle(), dto.getContent());
        } else {
            // userId=0 是广播通知的历史约定，查询端需按自己的场景决定是否合并广播消息。
            notificationService.notifySystem(0L, dto.getTitle(), dto.getContent());
        }
    }

    @Operation(summary = "发送系统通知给全体用户（写入 userId=0 的广播通知）")
    @OperationLog(module = "notification", action = "BROADCAST_NOTICE", description = "广播系统通知", logArgs = false)
    @PostMapping("/broadcast")
    public Result<Void> broadcast(@Valid @RequestBody BroadcastNotificationDTO dto) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        // userId=0 表示广播通知
        notificationService.notifySystem(0L, dto.getTitle(), dto.getContent());
        return Result.success();
    }

    @Operation(summary = "删除通知")
    @OperationLog(module = "notification", action = "DELETE_NOTICE", description = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        notificationMapper.deleteById(id);
        return Result.success();
    }

    @Data
    public static class SendNotificationDTO {
        private List<Long> userIds;
        private Long targetUserId;
        private String targetType;
        private String type;
        @NotBlank(message = "标题不能为空")
        private String title;
        @NotBlank(message = "内容不能为空")
        private String content;
    }

    @Data
    public static class BroadcastNotificationDTO {
        @NotBlank(message = "标题不能为空")
        private String title;
        @NotBlank(message = "内容不能为空")
        private String content;
    }
}
