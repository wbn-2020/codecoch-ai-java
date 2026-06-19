-- V4_030: bind resume-job match reports to optional resume versions.
-- Compatible migration: adds only nullable columns and non-unique indexes.
-- Uses information_schema + prepared DDL instead of temporary routines so
-- test/staging DB accounts do not need routine-creation permission.

SET @schema_name = DATABASE();

SET @resume_version_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'resume_job_match_report'
    AND column_name = 'resume_version_id'
);
SET @sql = IF(
  @resume_version_column_exists = 0,
  'ALTER TABLE `resume_job_match_report` ADD COLUMN `resume_version_id` BIGINT DEFAULT NULL COMMENT ''Optional resume_version id used as the match snapshot source'' AFTER `resume_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_resume_match_resume_version_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'resume_job_match_report'
    AND index_name = 'idx_resume_match_resume_version'
);
SET @sql = IF(
  @idx_resume_match_resume_version_exists = 0,
  'ALTER TABLE `resume_job_match_report` ADD INDEX `idx_resume_match_resume_version` (`resume_version_id`, `deleted`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_resume_match_user_version_job_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'resume_job_match_report'
    AND index_name = 'idx_resume_match_user_version_job'
);
SET @sql = IF(
  @idx_resume_match_user_version_job_exists = 0,
  'ALTER TABLE `resume_job_match_report` ADD INDEX `idx_resume_match_user_version_job` (`user_id`, `resume_version_id`, `target_job_id`, `deleted`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
