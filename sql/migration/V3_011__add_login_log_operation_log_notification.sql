-- ============================================================
-- V3_011: 补齐 V3 新增字段
-- 说明：V3_008/V3_009 已负责创建 login_log / operation_log / notification。
-- 本迁移只做兼容增量，避免重复 CREATE TABLE 导致新旧库结构分叉。
-- ============================================================

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

DELIMITER //

CREATE PROCEDURE add_column_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT,
  IN after_column_value VARCHAR(64)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @alter_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ',
      column_definition_value,
      IF(after_column_value IS NULL OR after_column_value = '', '', CONCAT(' AFTER `', after_column_value, '`'))
    );
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

CREATE PROCEDURE add_index_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN index_name_value VARCHAR(64),
  IN index_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND index_name = index_name_value
  ) THEN
    SET @alter_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

DELIMITER ;

-- login_log: 对齐 system-service LoginLog 实体，同时兼容 V3_008 旧字段。
CALL add_column_if_not_exists('login_log', 'login_status',
  'VARCHAR(16) NOT NULL DEFAULT ''SUCCESS'' COMMENT ''SUCCESS / FAILED''', 'login_type');
CALL add_column_if_not_exists('login_log', 'fail_reason',
  'VARCHAR(255) DEFAULT NULL', 'user_agent');
CALL add_column_if_not_exists('login_log', 'trace_id',
  'VARCHAR(64) DEFAULT NULL', 'fail_reason');
CALL add_column_if_not_exists('login_log', 'login_time',
  'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP', 'trace_id');
CALL add_index_if_not_exists('login_log', 'idx_login_log_user_id',
  'INDEX `idx_login_log_user_id` (`user_id`)');
CALL add_index_if_not_exists('login_log', 'idx_login_log_time',
  'INDEX `idx_login_log_time` (`login_time`)');

-- operation_log: V3_008 已有主体字段，这里补齐常用时间索引。
CALL add_index_if_not_exists('operation_log', 'idx_oplog_user_id',
  'INDEX `idx_oplog_user_id` (`user_id`)');
CALL add_index_if_not_exists('operation_log', 'idx_oplog_created_at',
  'INDEX `idx_oplog_created_at` (`created_at`)');

-- notification: V3_009 已有主体字段，这里补 updated_at 与创建时间索引。
CALL add_column_if_not_exists('notification', 'updated_at',
  'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'created_at');
CALL add_index_if_not_exists('notification', 'idx_notification_created_at',
  'INDEX `idx_notification_created_at` (`created_at`)');

-- ai_call_log 增强字段。
CALL add_column_if_not_exists('ai_call_log', 'route_trace',
  'VARCHAR(128) DEFAULT NULL COMMENT ''路由轨迹''', 'error_message');
CALL add_column_if_not_exists('ai_call_log', 'estimated_cost',
  'DECIMAL(10,6) DEFAULT NULL COMMENT ''预估费用（元）''', 'route_trace');

-- file_info OSS 字段（V3_006 已加时本迁移为空操作）。
CALL add_column_if_not_exists('file_info', 'oss_key',
  'VARCHAR(512) DEFAULT NULL COMMENT ''OSS Key''', 'storage_path');
CALL add_column_if_not_exists('file_info', 'bucket',
  'VARCHAR(128) DEFAULT NULL COMMENT ''OSS Bucket''', 'oss_key');
CALL add_column_if_not_exists('file_info', 'etag',
  'VARCHAR(128) DEFAULT NULL COMMENT ''OSS ETag''', 'bucket');
CALL add_column_if_not_exists('file_info', 'md5',
  'VARCHAR(64) DEFAULT NULL COMMENT ''文件MD5''', 'etag');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
