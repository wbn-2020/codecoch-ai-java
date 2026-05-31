-- V4_020: Personal knowledge eval citation trust fields.
-- Keep this migration idempotent because some environments replay V4 SQL manually during validation.

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
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @alter_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ',
      column_definition_value,
      IF(after_column_value IS NULL OR after_column_value = '', '', CONCAT(' AFTER `', after_column_value, '`'))
    );
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

DELIMITER ;

CALL add_column_if_not_exists('personal_knowledge_eval_result', 'citation_valid',
  'TINYINT DEFAULT NULL COMMENT ''1 if generated answer cites retrieved references correctly''', 'reference_count');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'answer_grounded',
  'TINYINT DEFAULT NULL COMMENT ''1 if generated answer is grounded by valid retrieved citations''', 'citation_valid');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'answer_excerpt',
  'VARCHAR(1000) DEFAULT NULL COMMENT ''Generated answer excerpt captured during evaluation''', 'answer_grounded');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'citation_warning',
  'VARCHAR(1000) DEFAULT NULL COMMENT ''Citation or grounding warning captured during evaluation''', 'answer_excerpt');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
