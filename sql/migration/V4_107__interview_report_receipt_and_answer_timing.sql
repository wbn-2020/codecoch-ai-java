-- Persist the report dispatch receipt and server-side answer timing facts.
SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_message')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_message'
                     AND column_name = 'question_presented_at'),
    'ALTER TABLE `interview_message`
       ADD COLUMN `question_presented_at` DATETIME NULL
       COMMENT ''Authoritative server time at which a question became answerable'' AFTER `comment`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_message')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_message'
                     AND column_name = 'answer_duration_seconds'),
    'ALTER TABLE `interview_message`
       ADD COLUMN `answer_duration_seconds` INT NULL
       COMMENT ''Server-derived duration between question presentation and answer submission'' AFTER `question_presented_at`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_report'
                     AND column_name = 'async_message_id'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `async_message_id` VARCHAR(128) NULL AFTER `generation_token`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_report'
                     AND column_name = 'async_trace_id'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `async_trace_id` VARCHAR(128) NULL AFTER `async_message_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_report'
                     AND column_name = 'async_biz_type'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `async_biz_type` VARCHAR(96) NULL AFTER `async_trace_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_report'
                     AND column_name = 'async_biz_id'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `async_biz_id` VARCHAR(96) NULL AFTER `async_biz_type`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_report'
                     AND column_name = 'async_send_status'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `async_send_status` VARCHAR(48) NULL AFTER `async_biz_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_report'
                     AND column_name = 'async_dispatch_mode'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `async_dispatch_mode` VARCHAR(32) NULL AFTER `async_send_status`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
