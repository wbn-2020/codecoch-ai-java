package com.codecoachai.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 异步任务表 async_task。
 * 每条 MQ 消息消费时落库，承担任务追踪与重试。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("async_task")
public class AsyncTask extends BaseEntity {

    /** MQ 消息 ID（messageId），唯一索引 */
    private String messageId;

    /** 业务类型（resume.parse / interview.report 等） */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 触发用户 ID */
    private Long userId;

    /** 链路追踪 ID */
    private String traceId;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAILED / DEAD */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 失败原因（最近一次） */
    private String failureReason;

    /** 任务请求负载（JSON） */
    private String payload;

    /** 任务结果（JSON，可空） */
    private String result;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
