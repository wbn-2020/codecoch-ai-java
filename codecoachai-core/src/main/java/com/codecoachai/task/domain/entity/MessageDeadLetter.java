package com.codecoachai.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 死信记录表 message_dead_letter。
 * 当 AsyncTask 重试次数耗尽，会写入此表并触发管理员通知。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_dead_letter")
public class MessageDeadLetter extends BaseEntity {

    private String messageId;
    private String bizType;
    private String bizId;
    private Long userId;
    private String traceId;
    private String payload;
    private String lastFailureReason;
    private Integer totalRetry;

    /** 处理状态：UNHANDLED / RECOVERED / IGNORED */
    private String handleStatus;

    private String handleNote;
    private Long handlerUserId;
}
