-- V4_035: add per-report generation token for async report generation isolation.
-- Compatible migration: adds nullable column only; no query path currently needs an index.

SET @schema_name = DATABASE();

SET @interview_report_generation_token_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'interview_report'
    AND column_name = 'generation_token'
);
SET @sql = IF(
  @interview_report_generation_token_column_exists = 0,
  'ALTER TABLE `interview_report` ADD COLUMN `generation_token` VARCHAR(64) NULL COMMENT ''Current report generation round token'' AFTER `failure_reason`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
