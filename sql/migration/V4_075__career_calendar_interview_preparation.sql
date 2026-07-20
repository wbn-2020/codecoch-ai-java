-- Persist the current interview preparation package on each career calendar event.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
           AND column_name = 'preparation_json'
    ),
    'ALTER TABLE `career_calendar_event`
       ADD COLUMN `preparation_json` MEDIUMTEXT NULL AFTER `import_batch_id`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
           AND column_name = 'preparation_status'
    ),
    'ALTER TABLE `career_calendar_event`
       ADD COLUMN `preparation_status` VARCHAR(24) NULL AFTER `preparation_json`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
           AND column_name = 'preparation_ai_call_log_id'
    ),
    'ALTER TABLE `career_calendar_event`
       ADD COLUMN `preparation_ai_call_log_id` BIGINT NULL AFTER `preparation_status`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
           AND column_name = 'preparation_generated_at'
    ),
    'ALTER TABLE `career_calendar_event`
       ADD COLUMN `preparation_generated_at` DATETIME NULL AFTER `preparation_ai_call_log_id`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'career_calendar_event'
           AND column_name = 'preparation_source_hash'
    ),
    'ALTER TABLE `career_calendar_event`
       ADD COLUMN `preparation_source_hash` CHAR(64) NULL AFTER `preparation_generated_at`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
