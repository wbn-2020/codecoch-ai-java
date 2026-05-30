-- V4_011: Personal knowledge hard dedup fingerprints.
-- Adds stable SHA-256 fingerprints for document and chunk exact duplicate checks per user.

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

CALL add_column_if_not_exists('personal_knowledge_document', 'content_hash',
  'CHAR(64) DEFAULT NULL COMMENT ''SHA-256 of normalized document content for hard duplicate checks''', 'content');
CALL add_column_if_not_exists('personal_knowledge_chunk', 'chunk_hash',
  'CHAR(64) DEFAULT NULL COMMENT ''SHA-256 of normalized chunk content for hard duplicate checks''', 'content');

UPDATE `personal_knowledge_document`
SET `content_hash` = SHA2(
  LOWER(REGEXP_REPLACE(COALESCE(`content`, ''), '[[:space:][:punct:]]+', '')),
  256
)
WHERE `content_hash` IS NULL
  AND COALESCE(`content`, '') <> '';

UPDATE `personal_knowledge_chunk`
SET `chunk_hash` = SHA2(
  LOWER(REGEXP_REPLACE(COALESCE(`content`, ''), '[[:space:][:punct:]]+', '')),
  256
)
WHERE `chunk_hash` IS NULL
  AND COALESCE(`content`, '') <> '';

CALL add_index_if_not_exists('personal_knowledge_document', 'idx_personal_doc_user_content_hash',
  'INDEX `idx_personal_doc_user_content_hash` (`user_id`, `content_hash`)');
CALL add_index_if_not_exists('personal_knowledge_chunk', 'idx_personal_chunk_user_hash',
  'INDEX `idx_personal_chunk_user_hash` (`user_id`, `chunk_hash`)');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
