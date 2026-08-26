-- V4_125: persist bounded resume presentation settings with the resume aggregate.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'resume'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'resume'
          AND column_name = 'presentation_config_json'
    ),
    'ALTER TABLE `resume`
       ADD COLUMN `presentation_config_json` TEXT NULL
       COMMENT ''Bounded data-only presentation configuration''
       AFTER `summary`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
