-- ============================================================
-- V3_007__async_task_and_dead_letter.sql
-- 用途：异步任务中心两张核心表
-- 影响：codecoachai-task 服务
-- ============================================================

CREATE TABLE IF NOT EXISTS `async_task` (
  `id`               BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `message_id`       VARCHAR(64)  NOT NULL                COMMENT 'MQ 消息 ID（messageId），唯一',
  `biz_type`         VARCHAR(64)  NOT NULL                COMMENT '业务类型，例：resume.parse / interview.report',
  `biz_id`           VARCHAR(64)           DEFAULT NULL   COMMENT '业务 ID',
  `user_id`          BIGINT(20)            DEFAULT NULL   COMMENT '触发用户 ID',
  `trace_id`         VARCHAR(64)           DEFAULT NULL   COMMENT '链路追踪 ID',
  `status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/DEAD',
  `retry_count`      INT(11)      NOT NULL DEFAULT 0      COMMENT '已重试次数',
  `max_retry`        INT(11)      NOT NULL DEFAULT 3      COMMENT '最大重试次数',
  `failure_reason`   TEXT                  DEFAULT NULL   COMMENT '失败原因（最近一次）',
  `payload`          MEDIUMTEXT            DEFAULT NULL   COMMENT '任务请求负载 JSON',
  `result`           MEDIUMTEXT            DEFAULT NULL   COMMENT '任务结果 JSON',
  `started_at`       DATETIME              DEFAULT NULL   COMMENT '开始时间',
  `completed_at`     DATETIME              DEFAULT NULL   COMMENT '完成时间',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_async_task_msg_id` (`message_id`),
  KEY `idx_async_task_biz` (`biz_type`, `status`),
  KEY `idx_async_task_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务记录';

CREATE TABLE IF NOT EXISTS `message_dead_letter` (
  `id`                  BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `message_id`          VARCHAR(64)  NOT NULL                COMMENT '原 MQ 消息 ID',
  `biz_type`            VARCHAR(64)  NOT NULL                COMMENT '业务类型',
  `biz_id`              VARCHAR(64)           DEFAULT NULL   COMMENT '业务 ID',
  `user_id`             BIGINT(20)            DEFAULT NULL,
  `trace_id`            VARCHAR(64)           DEFAULT NULL,
  `payload`             MEDIUMTEXT            DEFAULT NULL,
  `last_failure_reason` TEXT                  DEFAULT NULL,
  `total_retry`         INT(11)      NOT NULL DEFAULT 0,
  `handle_status`       VARCHAR(16)  NOT NULL DEFAULT 'UNHANDLED' COMMENT 'UNHANDLED/RECOVERED/IGNORED',
  `handle_note`         VARCHAR(500)          DEFAULT NULL,
  `handler_user_id`     BIGINT(20)            DEFAULT NULL,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`             TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dlq_msg_id` (`message_id`),
  KEY `idx_dlq_handle_status` (`handle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信记录';
