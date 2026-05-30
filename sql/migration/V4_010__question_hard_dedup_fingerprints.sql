-- V4_010: Question hard dedup fingerprints.
-- Adds stable SHA-256 fingerprints for title-level and content-level duplicate checks.

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

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

CREATE PROCEDURE add_index_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN index_name_value VARCHAR(64),
  IN index_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND index_name = index_name_value
  ) THEN
    SET @alter_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

DELIMITER ;

CALL add_column_if_not_exists('question', 'normalized_title_hash',
  'CHAR(64) DEFAULT NULL COMMENT ''SHA-256 of normalized_title for hard duplicate checks''', 'normalized_title');
CALL add_column_if_not_exists('question', 'content_hash',
  'CHAR(64) DEFAULT NULL COMMENT ''SHA-256 of normalized title/content/answer/analysis for hard duplicate checks''', 'normalized_title_hash');

UPDATE `question`
SET `normalized_title_hash` = SHA2(`normalized_title`, 256)
WHERE `normalized_title_hash` IS NULL
  AND `normalized_title` IS NOT NULL
  AND `normalized_title` <> '';

UPDATE `question`
SET `content_hash` = SHA2(
  LOWER(
    REGEXP_REPLACE(
      CONCAT_WS('\n', COALESCE(`title`, ''), COALESCE(`content`, ''), COALESCE(`reference_answer`, ''), COALESCE(`analysis`, '')),
      '[[:space:][:punct:]]+',
      ''
    )
  ),
  256
)
WHERE `content_hash` IS NULL
  AND CONCAT_WS('', COALESCE(`title`, ''), COALESCE(`content`, ''), COALESCE(`reference_answer`, ''), COALESCE(`analysis`, '')) <> '';

CALL add_index_if_not_exists('question', 'idx_question_normalized_title_hash',
  'INDEX `idx_question_normalized_title_hash` (`normalized_title_hash`)');
CALL add_index_if_not_exists('question', 'idx_question_content_hash',
  'INDEX `idx_question_content_hash` (`content_hash`)');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;