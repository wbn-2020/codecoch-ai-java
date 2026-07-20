-- Idempotently add AI narrative metadata to daily agent reviews.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'confidence_level'
    ),
    'ALTER TABLE `agent_review`
       ADD COLUMN `confidence_level` VARCHAR(16) NULL AFTER `ai_call_log_id`',
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
          AND table_name = 'agent_review'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'fallback'
    ),
    'ALTER TABLE `agent_review`
       ADD COLUMN `fallback` TINYINT NOT NULL DEFAULT 0 AFTER `confidence_level`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
