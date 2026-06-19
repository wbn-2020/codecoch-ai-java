-- V4_031: link personal job applications to optional resume-job match reports.
-- Compatible migration: adds only nullable column and non-unique index.
-- Uses information_schema + prepared DDL instead of temporary routines so
-- test/staging DB accounts do not need routine-creation permission.

SET @schema_name = DATABASE();

SET @job_application_match_report_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'job_application'
    AND column_name = 'match_report_id'
);
SET @sql = IF(
  @job_application_match_report_column_exists = 0,
  'ALTER TABLE `job_application` ADD COLUMN `match_report_id` BIGINT DEFAULT NULL COMMENT ''Optional resume_job_match_report id used to create this application'' AFTER `resume_version_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_job_application_match_report_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'job_application'
    AND index_name = 'idx_job_application_match_report'
);
SET @sql = IF(
  @idx_job_application_match_report_exists = 0,
  'ALTER TABLE `job_application` ADD INDEX `idx_job_application_match_report` (`match_report_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
