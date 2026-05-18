-- ============================================================
-- V3_011: 补齐 V3 新增表和字段
-- 涉及：login_log / operation_log / notification / async_task 补字段 / ai_call_log 补字段
-- ============================================================

-- ---------- 1. login_log ----------
CREATE TABLE IF NOT EXISTS `login_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID（登录失败时可能为空）',
    `username` VARCHAR(64) DEFAULT NULL,
    `login_type` VARCHAR(32) NOT NULL DEFAULT 'PASSWORD' COMMENT 'PASSWORD / OAUTH / LOGOUT',
    `login_status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED',
    `ip` VARCHAR(64) DEFAULT NULL,
    `user_agent` VARCHAR(512) DEFAULT NULL,
    `fail_reason` VARCHAR(255) DEFAULT NULL,
    `trace_id` VARCHAR(64) DEFAULT NULL,
    `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_login_log_user_id` (`user_id`),
    KEY `idx_login_log_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- ---------- 2. operation_log ----------
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `trace_id` VARCHAR(64) DEFAULT NULL,
    `user_id` BIGINT DEFAULT NULL,
    `username` VARCHAR(64) DEFAULT NULL,
    `module` VARCHAR(64) NOT NULL COMMENT '业务模块',
    `action` VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/APPROVE/EXPORT',
    `target_type` VARCHAR(64) DEFAULT NULL COMMENT '操作对象类型',
    `target_id` VARCHAR(64) DEFAULT NULL COMMENT '操作对象ID',
    `method` VARCHAR(128) DEFAULT NULL COMMENT 'Controller#method',
    `request_uri` VARCHAR(255) DEFAULT NULL,
    `request_args` TEXT DEFAULT NULL,
    `response` TEXT DEFAULT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED',
    `error_msg` VARCHAR(2000) DEFAULT NULL,
    `ip` VARCHAR(64) DEFAULT NULL,
    `user_agent` VARCHAR(512) DEFAULT NULL,
    `cost_ms` BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_oplog_user_id` (`user_id`),
    KEY `idx_oplog_module_action` (`module`, `action`),
    KEY `idx_oplog_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- ---------- 3. notification ----------
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '接收用户ID（0=系统公告）',
    `type` VARCHAR(32) NOT NULL COMMENT 'SYSTEM/TASK_DONE/TASK_FAILED/REVIEW_RESULT/SECURITY',
    `title` VARCHAR(128) NOT NULL,
    `content` VARCHAR(1000) DEFAULT NULL,
    `biz_type` VARCHAR(64) DEFAULT NULL,
    `biz_id` VARCHAR(64) DEFAULT NULL,
    `read_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
    `read_at` DATETIME DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notification_user_read` (`user_id`, `read_status`),
    KEY `idx_notification_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

-- ---------- 4. ai_call_log 补字段 ----------
ALTER TABLE `ai_call_log`
    ADD COLUMN IF NOT EXISTS `route_trace` VARCHAR(128) DEFAULT NULL COMMENT '路由轨迹' AFTER `error_message`,
    ADD COLUMN IF NOT EXISTS `estimated_cost` DECIMAL(10,6) DEFAULT NULL COMMENT '预估费用（元）' AFTER `route_trace`;

-- ---------- 5. file_info 补字段（V3_006 可能已加，这里做幂等） ----------
ALTER TABLE `file_info`
    ADD COLUMN IF NOT EXISTS `oss_key` VARCHAR(512) DEFAULT NULL COMMENT 'OSS Key' AFTER `storage_path`,
    ADD COLUMN IF NOT EXISTS `bucket` VARCHAR(128) DEFAULT NULL COMMENT 'OSS Bucket' AFTER `oss_key`,
    ADD COLUMN IF NOT EXISTS `etag` VARCHAR(128) DEFAULT NULL COMMENT 'OSS ETag' AFTER `bucket`,
    ADD COLUMN IF NOT EXISTS `md5` VARCHAR(64) DEFAULT NULL COMMENT '文件MD5' AFTER `etag`;
