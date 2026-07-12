-- Idempotently widen career import row JSON audit payloads.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'career_import_row'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'career_import_row'
          AND column_name = 'raw_data_json'
          AND data_type <> 'mediumtext'
    ),
    'ALTER TABLE `career_import_row`
       MODIFY COLUMN `raw_data_json` MEDIUMTEXT NULL',
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
          AND table_name = 'career_import_row'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'career_import_row'
          AND column_name = 'duplicate_candidates_json'
          AND data_type <> 'mediumtext'
    ),
    'ALTER TABLE `career_import_row`
       MODIFY COLUMN `duplicate_candidates_json` MEDIUMTEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
