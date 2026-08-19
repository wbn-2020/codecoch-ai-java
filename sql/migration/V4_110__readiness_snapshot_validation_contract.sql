DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //

CREATE PROCEDURE add_column_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT,
  IN after_column_value VARCHAR(64)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = table_name_value
       AND column_name = column_name_value
  ) THEN
    SET @alter_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ',
      column_definition_value,
      IF(after_column_value IS NULL OR after_column_value = '',
         '', CONCAT(' AFTER `', after_column_value, '`'))
    );
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

DELIMITER ;

CALL add_column_if_not_exists('job_readiness_snapshot', 'source_hash',
  'VARCHAR(64) DEFAULT NULL COMMENT ''source matrix fingerprint''', 'snapshot_hash');
CALL add_column_if_not_exists('job_readiness_snapshot', 'schema_version',
  'VARCHAR(64) DEFAULT NULL COMMENT ''dimension JSON schema version''', 'policy_version');
CALL add_column_if_not_exists('job_readiness_snapshot', 'validation_status',
  'VARCHAR(32) DEFAULT NULL COMMENT ''VALID/EMPTY/INVALID_JSON/INVALID_STRUCTURE/UNSUPPORTED_SCHEMA''',
  'schema_version');
CALL add_column_if_not_exists('job_readiness_snapshot', 'repair_batch_id',
  'VARCHAR(96) DEFAULT NULL COMMENT ''auditable repair batch identifier''', 'validation_status');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
