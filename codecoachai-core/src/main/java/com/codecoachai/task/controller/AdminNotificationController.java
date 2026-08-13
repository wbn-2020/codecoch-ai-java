package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.mapper.NotificationMapper;
import com.codecoachai.task.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final AdminOperationConfirmationGuard operationConfirmationGuard;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "分页查询所有通知")
    @GetMapping
    public Result<PageResult<Notification>> page(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sendStatus,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer readStatus) {
        adminPermissionGuard.require(PERM_NOTICE_LIST);
        // status 是早期前端筛选字段，readStatus 是新字段；两者都保留，避免旧管理页筛选失效。
        Integer resolvedReadStatus = readStatus != null ? readStatus : status;
        long safePageNo = safePageNo(pageNo);
        long safePageSize = safePageSize(pageSize);
        try {
            Page<Notification> page = notificationMapper.selectPage(
                    Page.of(safePageNo, safePageSize),
                    new LambdaQueryWrapper<Notification>()
                            .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                    .like(Notification::getTitle, keyword)
                                    .or().like(Notification::getContent, keyword)
                                    .or().like(Notification::getType, keyword)
                                    .or().like(Notification::getBizType, keyword)
                                    .or().like(Notification::getBizId, keyword))
                            .eq(userId != null, Notification::getUserId, userId)
                            .eq(StringUtils.hasText(type), Notification::getType, type)
                            .eq(StringUtils.hasText(sendStatus), Notification::getSendStatus, sendStatus)
                            .eq(resolvedReadStatus != null, Notification::getReadStatus, resolvedReadStatus)
                            .orderByDesc(Notification::getCreatedAt));
            return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
        } catch (DataAccessException ex) {
            return Result.success(legacyPage(safePageNo, safePageSize, keyword, userId, type, sendStatus, resolvedReadStatus));
        }
    }

    private PageResult<Notification> legacyPage(long pageNo, long pageSize, String keyword, Long userId, String type,
                                                String sendStatus, Integer readStatus) {
        boolean hasSendStatus = columnExists("notification", "send_status");
        boolean hasSendError = columnExists("notification", "send_error");
        boolean hasSentAt = columnExists("notification", "sent_at");
        if (StringUtils.hasText(sendStatus) && !hasSendStatus) {
            return PageResult.of(List.of(), 0L, pageNo, pageSize);
        }

        StringBuilder where = new StringBuilder(" FROM notification WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (title LIKE ? OR content LIKE ? OR type LIKE ? OR biz_type LIKE ? OR biz_id LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (userId != null) {
            where.append(" AND user_id = ?");
            args.add(userId);
        }
        if (StringUtils.hasText(type)) {
            where.append(" AND type = ?");
            args.add(type);
        }
        if (StringUtils.hasText(sendStatus) && hasSendStatus) {
            where.append(" AND send_status = ?");
            args.add(sendStatus);
        }
        if (readStatus != null) {
            where.append(" AND read_status = ?");
            args.add(readStatus);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1)" + where, Long.class, args.toArray());
        String select = "SELECT id, user_id, type, title, content, biz_type, biz_id, read_status, read_at, created_at, updated_at, deleted"
                + (hasSendStatus ? ", send_status" : "")
                + (hasSendError ? ", send_error" : "")
                + (hasSentAt ? ", sent_at" : "")
                + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNo - 1) * pageSize);
        List<Notification> records = jdbcTemplate.queryForList(select, pageArgs.toArray())
                .stream()
                .map(row -> toNotification(row, hasSendStatus, hasSendError, hasSentAt))
                .toList();
        return PageResult.of(records, total == null ? 0L : total, pageNo, pageSize);
    }

    private Notification toNotification(Map<String, Object> row, boolean hasSendStatus, boolean hasSendError, boolean hasSentAt) {
        Notification notification = new Notification();
        notification.setId(longValue(row.get("id")));
        notification.setUserId(longValue(row.get("user_id")));
        notification.setType(stringValue(row.get("type")));
        notification.setTitle(stringValue(row.get("title")));
        notification.setContent(stringValue(row.get("content")));
        notification.setBizType(stringValue(row.get("biz_type")));
        notification.setBizId(stringValue(row.get("biz_id")));
        notification.setReadStatus(intValue(row.get("read_status")));
        notification.setReadAt(dateTimeValue(row.get("read_at")));
        notification.setCreatedAt(dateTimeValue(row.get("created_at")));
        notification.setUpdatedAt(dateTimeValue(row.get("updated_at")));
        notification.setDeleted(intValue(row.get("deleted")));
        notification.setSendStatus(hasSendStatus ? stringValue(row.get("send_status")) : "SUCCESS");
        notification.setSendError(hasSendError ? stringValue(row.get("send_error")) : null);
        notification.setSentAt(hasSentAt ? dateTimeValue(row.get("sent_at")) : notification.getCreatedAt());
        return notification;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private long safePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    @Operation(summary = "发送系统通知给指定用户")
    @OperationLog(module = "notification", action = "SEND_NOTICE", description = "发送系统通知", logArgs = false, logResponse = false)
    @PostMapping("/send")
    public Result<Void> send(@Valid @RequestBody SendNotificationDTO dto) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        runConfirmedOperation("notice-send:" + notificationTargetKey(dto),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> doSend(dto));
        return Result.success();
    }

    @OperationLog(module = "notification", action = "SEND_NOTICE_COMPAT", description = "兼容入口发送系统通知", logArgs = false, logResponse = false)
    @PostMapping
    public Result<Void> sendCompat(@Valid @RequestBody SendNotificationDTO dto) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        // 兼容旧管理端直接 POST /admin/notifications 的发送入口，实际发送规则统一收口到 doSend。
        runConfirmedOperation("notice-send:" + notificationTargetKey(dto),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> doSend(dto));
        return Result.success();
    }

    private void doSend(SendNotificationDTO dto) {
        String type = StringUtils.hasText(dto.getType()) ? dto.getType() : "SYSTEM";
        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            for (Long uid : dto.getUserIds()) {
                notificationService.notify(uid, type, null, null, dto.getTitle(), dto.getContent());
            }
        } else if (dto.getTargetUserId() != null) {
            notificationService.notify(dto.getTargetUserId(), type, null, null, dto.getTitle(), dto.getContent());
        } else {
            // userId=0 是广播通知的历史约定，查询端需按自己的场景决定是否合并广播消息。
            notificationService.notify(0L, type, null, null, dto.getTitle(), dto.getContent());
        }
    }

    @Operation(summary = "发送系统通知给全体用户（写入 userId=0 的广播通知）")
    @OperationLog(module = "notification", action = "BROADCAST_NOTICE", description = "广播系统通知", logArgs = false, logResponse = false)
    @PostMapping("/broadcast")
    public Result<Void> broadcast(@Valid @RequestBody BroadcastNotificationDTO dto) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        // userId=0 表示广播通知
        runConfirmedOperation("notice-broadcast", dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> notificationService.notifySystem(0L, dto.getTitle(), dto.getContent()));
        return Result.success();
    }

    @Operation(summary = "删除通知")
    @OperationLog(module = "notification", action = "DELETE_NOTICE", description = "删除通知", logArgs = false, logResponse = false)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_NOTICE_WRITE);
        runConfirmedOperation("notice-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> notificationMapper.deleteById(id));
        return Result.success();
    }

    private String notificationTargetKey(SendNotificationDTO dto) {
        if (dto == null) {
            return "UNKNOWN";
        }
        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            return "users:" + dto.getUserIds();
        }
        if (dto.getTargetUserId() != null) {
            return "user:" + dto.getTargetUserId();
        }
        return "broadcast";
    }

    private void runConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                       String reason, String idempotencyKey, Runnable action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
        try {
            action.run();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
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
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }

    @Data
    public static class BroadcastNotificationDTO {
        @NotBlank(message = "标题不能为空")
        private String title;
        @NotBlank(message = "内容不能为空")
        private String content;
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }

    @Data
    public static class AdminOperationConfirmDTO {
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }
}
