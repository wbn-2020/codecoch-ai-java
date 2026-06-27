-- V4_034: persist Agent run AI result source for LLM/MOCK/FALLBACK observability.
-- Compatible migration: adds nullable column only; no query path currently needs an index.

SET @schema_name = DATABASE();

SET @agent_run_result_source_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_run'
    AND column_name = 'result_source'
);
SET @sql = IF(
  @agent_run_result_source_column_exists = 0,
  'ALTER TABLE `agent_run` ADD COLUMN `result_source` VARCHAR(32) NULL COMMENT ''AI result source: LLM/MOCK/FALLBACK'' AFTER `ai_call_log_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
