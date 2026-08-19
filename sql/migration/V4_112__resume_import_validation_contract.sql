-- Resume import contract metadata. This migration does not rewrite historical payloads.
-- information_schema guards keep the migration compatible with MySQL versions that do
-- not support ADD COLUMN/INDEX IF NOT EXISTS.

SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS add_resume_import_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_resume_import_column_if_missing(
    IN target_column VARCHAR(64),
    IN definition_sql TEXT
)
BEGIN
    DECLARE column_count INT DEFAULT 0;
    SELECT COUNT(1)
      INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'resume_analysis_record'
       AND column_name = target_column;
    IF column_count = 0 THEN
        SET @alter_sql = CONCAT(
            'ALTER TABLE `resume_analysis_record` ADD COLUMN `',
            target_column, '` ', definition_sql
        );
        PREPARE resume_import_stmt FROM @alter_sql;
        EXECUTE resume_import_stmt;
        DEALLOCATE PREPARE resume_import_stmt;
    END IF;
END//
DELIMITER ;

CALL add_resume_import_column_if_missing(
    'schema_version',
    'VARCHAR(32) DEFAULT NULL AFTER `structured_json`'
);
CALL add_resume_import_column_if_missing(
    'policy_version',
    'VARCHAR(64) DEFAULT NULL AFTER `schema_version`'
);
CALL add_resume_import_column_if_missing(
    'source_hash',
    'CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `policy_version`'
);
CALL add_resume_import_column_if_missing(
    'validation_status',
    'VARCHAR(32) DEFAULT NULL AFTER `source_hash`'
);
CALL add_resume_import_column_if_missing(
    'quality_report_json',
    'MEDIUMTEXT DEFAULT NULL AFTER `validation_status`'
);
CALL add_resume_import_column_if_missing(
    'generated_at',
    'DATETIME DEFAULT NULL AFTER `quality_report_json`'
);
CALL add_resume_import_column_if_missing(
    'repair_batch_id',
    'VARCHAR(64) DEFAULT NULL AFTER `generated_at`'
);

DROP PROCEDURE IF EXISTS add_resume_import_column_if_missing;

SET @index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = @schema_name
               AND table_name = 'resume_analysis_record'
               AND index_name = 'idx_resume_analysis_validation'
        ),
        'SELECT 1',
        'ALTER TABLE `resume_analysis_record` ADD INDEX `idx_resume_analysis_validation` (`validation_status`, `schema_version`, `parse_status`, `deleted`)'
    )
);
PREPARE resume_import_index_stmt FROM @index_sql;
EXECUTE resume_import_index_stmt;
DEALLOCATE PREPARE resume_import_index_stmt;

SET @index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = @schema_name
               AND table_name = 'resume_analysis_record'
               AND index_name = 'idx_resume_analysis_repair_batch'
        ),
        'SELECT 1',
        'ALTER TABLE `resume_analysis_record` ADD INDEX `idx_resume_analysis_repair_batch` (`repair_batch_id`, `deleted`)'
    )
);
PREPARE resume_import_index_stmt FROM @index_sql;
EXECUTE resume_import_index_stmt;
DEALLOCATE PREPARE resume_import_index_stmt;
