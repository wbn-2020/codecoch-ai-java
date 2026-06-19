-- V4_032: bind resume optimization records to optional target jobs.
-- Compatible migration: adds only nullable column and non-unique index.

SET @schema_name = DATABASE();

SET @resume_optimize_target_job_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'resume_optimize_record'
    AND column_name = 'target_job_id'
);
SET @sql = IF(
  @resume_optimize_target_job_column_exists = 0,
  'ALTER TABLE `resume_optimize_record` ADD COLUMN `target_job_id` BIGINT DEFAULT NULL COMMENT ''Optional target_job id used by Agent task completion evidence'' AFTER `resume_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_resume_optimize_user_target_status_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'resume_optimize_record'
    AND index_name = 'idx_resume_optimize_user_target_status'
);
SET @sql = IF(
  @idx_resume_optimize_user_target_status_exists = 0,
  'ALTER TABLE `resume_optimize_record` ADD INDEX `idx_resume_optimize_user_target_status` (`user_id`, `target_job_id`, `optimize_status`, `deleted`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
