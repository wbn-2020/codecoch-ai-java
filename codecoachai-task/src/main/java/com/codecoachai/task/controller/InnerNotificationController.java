package com.codecoachai.task.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.service.NotificationCommandService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部通知写入接口。供其他服务通过 Feign 调用，发送站内通知。
 * 仅限内部调用（/inner/** 由网关限制为内部流量）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/notifications")
public class InnerNotificationController {

    private final NotificationCommandService notificationCommandService;

    @PostMapping
    public Result<Long> create(@RequestBody InnerNotificationCreateDTO dto) {
        Long id = notificationCommandService.create(dto.getUserId(), dto.getType(), dto.getTitle(),
                dto.getContent(), dto.getBizType(), dto.getBizId());
        return Result.success(id);
    }

    @Data
    public static class InnerNotificationCreateDTO {
        /** 接收用户 ID，null 或 0 表示系统公告。 */
        private Long userId;
        /** TASK_DUE / INTERVIEW_REPORT_READY / KNOWLEDGE_INDEX_REBUILT / NEW_DUPLICATE_PENDING / SYSTEM 等。 */
        private String type;
        private String title;
        private String content;
        private String bizType;
        private String bizId;
    }
}
