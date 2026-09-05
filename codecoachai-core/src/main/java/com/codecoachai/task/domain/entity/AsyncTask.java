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

    /** 跨模块稳定执行 ID */
    private String executionId;

    /** 重试或补偿执行的父执行 ID */
    private String parentExecutionId;

    /** Agent run 或其他业务运行记录 ID */
    private Long runId;

    /** 当前执行尝试次数 */
    private Integer attemptNo;

    /** 防重复执行键 */
    private String idempotencyKey;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAILED / DEAD */
    private String status;

    /** RUNNING 任务的持久化所有权与 fencing token */
    private String leaseToken;

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

    /** 统一终态原因码 */
    private String terminalReasonCode;

    /** 人工治理状态，不影响实际任务状态机 */
    private String governanceStatus;

    /** 人工或系统记录的治理原因 */
    private String governanceReason;

    /** 负责处理的团队或角色 */
    private String governanceOwner;

    /** 最近一次治理状态更新时间 */
    private LocalDateTime governanceUpdatedAt;

    /** 操作前预览摘要的校验散列 */
    private String retryPreviewHash;

    /** 当前 RUNNING 租约的最近领取或续租时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
