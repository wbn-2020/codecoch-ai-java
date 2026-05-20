package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
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

    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    @Operation(summary = "分页查询所有通知")
    @GetMapping
    public Result<PageResult<Notification>> page(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer readStatus) {
        SecurityAssert.requireAdmin();
        Page<Notification> page = notificationMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(userId != null, Notification::getUserId, userId)
                        .eq(StringUtils.hasText(type), Notification::getType, type)
                        .eq(readStatus != null, Notification::getReadStatus, readStatus)
                        .orderByDesc(Notification::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "发送系统通知给指定用户")
    @PostMapping("/send")
    public Result<Void> send(@Valid @RequestBody SendNotificationDTO dto) {
        SecurityAssert.requireAdmin();
        doSend(dto);
        return Result.success();
    }

    @PostMapping
    public Result<Void> sendCompat(@Valid @RequestBody SendNotificationDTO dto) {
        SecurityAssert.requireAdmin();
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
            notificationService.notifySystem(0L, dto.getTitle(), dto.getContent());
        }
    }

    @Operation(summary = "发送系统通知给全体用户（写入 userId=0 的广播通知）")
    @PostMapping("/broadcast")
    public Result<Void> broadcast(@Valid @RequestBody BroadcastNotificationDTO dto) {
        SecurityAssert.requireAdmin();
        // userId=0 表示广播通知
        notificationService.notifySystem(0L, dto.getTitle(), dto.getContent());
        return Result.success();
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
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
