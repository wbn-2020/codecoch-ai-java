-- V4_033: add per-execution token for Agent run async isolation.
-- Compatible migration: adds nullable column and a non-unique index only.

SET @schema_name = DATABASE();

SET @agent_run_execution_token_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_run'
    AND column_name = 'execution_token'
);
SET @sql = IF(
  @agent_run_execution_token_column_exists = 0,
  'ALTER TABLE `agent_run` ADD COLUMN `execution_token` VARCHAR(64) NULL COMMENT ''Async execution isolation token'' AFTER `status`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_agent_run_execution_token_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'agent_run'
    AND index_name = 'idx_agent_run_execution_token'
);
SET @sql = IF(
  @idx_agent_run_execution_token_exists = 0,
  'ALTER TABLE `agent_run` ADD INDEX `idx_agent_run_execution_token` (`id`, `user_id`, `execution_token`, `status`, `deleted`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
