-- ============================================================
-- V3_008__operation_log_login_log.sql
-- 用途：操作日志 + 登录日志
-- ============================================================

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`            BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `trace_id`      VARCHAR(64)           DEFAULT NULL,
  `user_id`       BIGINT(20)            DEFAULT NULL  COMMENT '操作人 ID',
  `username`      VARCHAR(64)           DEFAULT NULL,
  `module`        VARCHAR(64)  NOT NULL                COMMENT '业务模块，例：question / resume / interview',
  `action`        VARCHAR(64)  NOT NULL                COMMENT '动作，例：CREATE / UPDATE / DELETE / APPROVE',
  `target_type`   VARCHAR(64)           DEFAULT NULL  COMMENT '操作目标类型',
  `target_id`     VARCHAR(64)           DEFAULT NULL  COMMENT '操作目标 ID',
  `method`        VARCHAR(255)          DEFAULT NULL  COMMENT '类#方法',
  `request_uri`   VARCHAR(500)          DEFAULT NULL,
  `request_args`  MEDIUMTEXT            DEFAULT NULL  COMMENT '入参 JSON（脱敏后）',
  `response`      MEDIUMTEXT            DEFAULT NULL  COMMENT '出参 JSON（可选记录）',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED',
  `error_msg`     VARCHAR(1000)         DEFAULT NULL,
  `ip`            VARCHAR(64)           DEFAULT NULL,
  `user_agent`    VARCHAR(255)          DEFAULT NULL,
  `cost_ms`       BIGINT(20)            DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_oplog_user` (`user_id`, `created_at`),
  KEY `idx_oplog_module_action` (`module`, `action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

CREATE TABLE IF NOT EXISTS `login_log` (
  `id`           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT(20)            DEFAULT NULL,
  `username`     VARCHAR(64)           DEFAULT NULL,
  `login_type`   VARCHAR(32)  NOT NULL DEFAULT 'PASSWORD' COMMENT 'PASSWORD / SMS / SSO',
  `status`       VARCHAR(16)  NOT NULL                    COMMENT 'SUCCESS / FAILED',
  `failure_reason` VARCHAR(255)         DEFAULT NULL,
  `ip`           VARCHAR(64)           DEFAULT NULL,
  `region`       VARCHAR(64)           DEFAULT NULL  COMMENT 'IP 归属地（异地登录检测）',
  `user_agent`   VARCHAR(255)          DEFAULT NULL,
  `device`       VARCHAR(64)           DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_loginlog_user` (`user_id`, `created_at`),
  KEY `idx_loginlog_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';
