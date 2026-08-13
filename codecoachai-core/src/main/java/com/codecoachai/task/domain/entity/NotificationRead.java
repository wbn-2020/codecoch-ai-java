package com.codecoachai.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_read")
public class NotificationRead extends BaseEntity {

    private Long notificationId;

    private Long userId;

    private LocalDateTime readAt;
}
