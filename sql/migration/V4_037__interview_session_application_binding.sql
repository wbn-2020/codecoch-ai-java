-- V4_037: persist optional job application binding on interview sessions.
-- Compatible migration: nullable column and non-unique index only.

SET @schema_name = DATABASE();

SET @interview_session_application_id_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'interview_session'
    AND column_name = 'application_id'
);
SET @sql = IF(
  @interview_session_application_id_column_exists = 0,
  'ALTER TABLE `interview_session` ADD COLUMN `application_id` BIGINT NULL COMMENT ''Linked job application ID'' AFTER `user_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_interview_session_application_id_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'interview_session'
    AND index_name = 'idx_interview_session_application_id'
);
SET @sql = IF(
  @idx_interview_session_application_id_exists = 0,
  'ALTER TABLE `interview_session` ADD INDEX `idx_interview_session_application_id` (`application_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
