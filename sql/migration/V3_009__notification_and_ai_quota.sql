-- ============================================================
-- V3_009__notification_and_ai_quota.sql
-- 用途：站内通知 + AI 配额限制 + AI 重试明细
-- ============================================================

CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT(20)   NOT NULL                  COMMENT '接收用户 ID（0 表示系统公告所有人）',
  `type`         VARCHAR(32)  NOT NULL                  COMMENT 'SYSTEM / TASK_DONE / TASK_FAILED / REVIEW_RESULT / SECURITY',
  `title`        VARCHAR(255) NOT NULL,
  `content`     TEXT                  DEFAULT NULL,
  `biz_type`     VARCHAR(64)           DEFAULT NULL    COMMENT '关联业务类型',
  `biz_id`       VARCHAR(64)           DEFAULT NULL    COMMENT '关联业务 ID（点击跳转用）',
  `read_status`  TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '0=未读 1=已读',
  `read_at`      DATETIME              DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_read` (`user_id`, `read_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

CREATE TABLE IF NOT EXISTS `ai_quota` (
  `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT(20)   NOT NULL,
  `quota_date`      DATE         NOT NULL                  COMMENT '配额日（按天）',
  `used_count`      INT(11)      NOT NULL DEFAULT 0        COMMENT '当日已使用次数',
  `used_input_tokens`  BIGINT(20) NOT NULL DEFAULT 0,
  `used_output_tokens` BIGINT(20) NOT NULL DEFAULT 0,
  `total_cost`      DECIMAL(10,4) NOT NULL DEFAULT 0      COMMENT '当日累计成本（单位元）',
  `daily_limit`     INT(11)               DEFAULT NULL    COMMENT '当日次数上限（NULL=用全局默认）',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quota_user_date` (`user_id`, `quota_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用配额';

CREATE TABLE IF NOT EXISTS `ai_retry_record` (
  `id`             BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `original_log_id` BIGINT(20)            DEFAULT NULL  COMMENT '关联 ai_call_log.id',
  `scene`          VARCHAR(64)           DEFAULT NULL,
  `model`          VARCHAR(64)           DEFAULT NULL,
  `attempt`        INT(11)      NOT NULL DEFAULT 1     COMMENT '第几次重试',
  `failure_type`   VARCHAR(32)           DEFAULT NULL  COMMENT 'TIMEOUT/HTTP_ERROR/EMPTY_RESPONSE/...',
  `failure_msg`    TEXT                  DEFAULT NULL,
  `duration_ms`    BIGINT(20)            DEFAULT NULL,
  `succeeded`      TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_retry_origin` (`original_log_id`),
  KEY `idx_retry_scene` (`scene`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 重试明细';
