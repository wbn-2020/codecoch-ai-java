SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_message')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name
                     AND table_name = 'interview_message'
                     AND column_name = 'generation_key'),
    'ALTER TABLE `interview_message`
       ADD COLUMN `generation_key` VARCHAR(64) DEFAULT NULL
       COMMENT ''Idempotency key for logical question generation''
       AFTER `parent_message_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name
             AND table_name = 'interview_message'
             AND column_name = 'generation_key')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name
                     AND table_name = 'interview_message'
                     AND index_name = 'uk_interview_message_generation_key'),
    'ALTER TABLE `interview_message`
       ADD UNIQUE KEY `uk_interview_message_generation_key` (`session_id`, `generation_key`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND EXISTS(SELECT 1 FROM information_schema.columns
               WHERE table_schema = @schema_name
                 AND table_name = 'interview_report'
                 AND column_name = 'session_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns
               WHERE table_schema = @schema_name
                 AND table_name = 'interview_report'
                 AND column_name = 'deleted'),
    'UPDATE `interview_report` older
       JOIN (
         SELECT `session_id`, MAX(`id`) AS keep_id
           FROM `interview_report`
          WHERE `deleted` = 0
          GROUP BY `session_id`
         HAVING COUNT(*) > 1
       ) duplicates
         ON duplicates.session_id = older.session_id
        SET older.deleted = 1
      WHERE older.deleted = 0
        AND older.id <> duplicates.keep_id',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name
                     AND table_name = 'interview_report'
                     AND column_name = 'active_session_id'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `active_session_id` BIGINT
       GENERATED ALWAYS AS (
         CASE WHEN `deleted` = 0 THEN `session_id` ELSE NULL END
       ) STORED
       COMMENT ''Unique guard for the active report of one interview session''',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name
             AND table_name = 'interview_report'
             AND column_name = 'active_session_id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name
                     AND table_name = 'interview_report'
                     AND index_name = 'uk_interview_report_active_session'),
    'ALTER TABLE `interview_report`
       ADD UNIQUE KEY `uk_interview_report_active_session` (`active_session_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
