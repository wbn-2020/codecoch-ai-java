-- V4_126: store the structured resume document next to the legacy flat columns it projects onto.

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
          AND column_name = 'document_json'
    ),
    'ALTER TABLE `resume`
       ADD COLUMN `document_json` MEDIUMTEXT NULL
       COMMENT ''Resume document v2; legacy columns stay as its projection''
       AFTER `presentation_config_json`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
