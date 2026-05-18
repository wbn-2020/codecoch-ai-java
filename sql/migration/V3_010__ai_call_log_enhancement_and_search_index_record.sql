-- ============================================================
-- V3_010__ai_call_log_enhancement_and_search_index_record.sql
-- 用途：
--   1. ai_call_log 加列：route_trace、token_cost、model（如果缺）
--   2. search_index_record 表（ES 同步兜底记录）
-- ============================================================

SET @dbname = DATABASE();
SET @tbl_log = 'ai_call_log';

-- ai_call_log.model：模型标识（如果原表没有就加）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl_log AND column_name = 'model') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN model VARCHAR(64) NULL COMMENT ''调用模型标识''',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ai_call_log.token_cost：成本（元）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl_log AND column_name = 'token_cost') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN token_cost DECIMAL(10,4) NULL DEFAULT 0 COMMENT ''本次调用估算成本（元）''',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ai_call_log.route_trace：路由轨迹（主→降级）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl_log AND column_name = 'route_trace') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN route_trace VARCHAR(255) NULL COMMENT ''路由轨迹，例：deepseek -> dashscope''',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ai_call_log.prompt_version_id：关联 Prompt 版本（如果原表没有就加）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl_log AND column_name = 'prompt_version_id') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN prompt_version_id BIGINT NULL COMMENT ''关联 prompt_template_version.id''',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- search_index_record：兜底的索引同步记录
CREATE TABLE IF NOT EXISTS `search_index_record` (
  `id`           BIGINT(20)  NOT NULL AUTO_INCREMENT,
  `index_name`   VARCHAR(64) NOT NULL                COMMENT 'cc_question / cc_resume / cc_interview',
  `doc_id`       VARCHAR(64) NOT NULL                COMMENT '索引文档 ID（业务主键）',
  `op`           VARCHAR(16) NOT NULL                COMMENT 'UPSERT / DELETE',
  `status`       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SUCCESS / FAILED',
  `failure_reason` VARCHAR(500)         DEFAULT NULL,
  `retry_count`  INT(11)     NOT NULL DEFAULT 0,
  `last_attempt_at` DATETIME           DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_search_index_doc` (`index_name`, `doc_id`, `op`, `created_at`),
  KEY `idx_search_index_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ES 索引同步兜底记录';
