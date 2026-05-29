-- V4_013: Vector index observability metadata for question dedupe and personal knowledge RAG.

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

CALL add_column_if_not_exists('question_embedding', 'embedding_model',
  'VARCHAR(128) DEFAULT NULL COMMENT ''Embedding model used for the indexed vector''', 'token_count');
CALL add_column_if_not_exists('question_embedding', 'embedding_dimension',
  'INT DEFAULT NULL COMMENT ''Embedding vector dimension''', 'embedding_model');
CALL add_column_if_not_exists('question_embedding', 'indexed_at',
  'DATETIME DEFAULT NULL COMMENT ''Last successful vector index time''', 'embedding_dimension');
CALL add_column_if_not_exists('question_embedding', 'index_status',
  'VARCHAR(32) NOT NULL DEFAULT ''PENDING'' COMMENT ''Vector index status: PENDING, INDEXED, FAILED, DELETED''', 'indexed_at');
CALL add_column_if_not_exists('question_embedding', 'last_error',
  'VARCHAR(512) DEFAULT NULL COMMENT ''Last vector index error message''', 'index_status');

CALL add_column_if_not_exists('personal_knowledge_chunk', 'embedding_model',
  'VARCHAR(128) DEFAULT NULL COMMENT ''Embedding model used for the indexed vector''', 'source_ref');
CALL add_column_if_not_exists('personal_knowledge_chunk', 'embedding_dimension',
  'INT DEFAULT NULL COMMENT ''Embedding vector dimension''', 'embedding_model');
CALL add_column_if_not_exists('personal_knowledge_chunk', 'indexed_at',
  'DATETIME DEFAULT NULL COMMENT ''Last successful vector index time''', 'embedding_dimension');
CALL add_column_if_not_exists('personal_knowledge_chunk', 'index_status',
  'VARCHAR(32) NOT NULL DEFAULT ''PENDING'' COMMENT ''Vector index status: PENDING, INDEXED, FAILED, DELETED''', 'indexed_at');
CALL add_column_if_not_exists('personal_knowledge_chunk', 'last_error',
  'VARCHAR(512) DEFAULT NULL COMMENT ''Last vector index error message''', 'index_status');

UPDATE `question_embedding`
SET `index_status` = CASE WHEN `deleted` = 1 THEN 'DELETED' ELSE COALESCE(NULLIF(`index_status`, ''), 'PENDING') END
WHERE `index_status` IS NULL OR `index_status` = '' OR `deleted` = 1;

UPDATE `personal_knowledge_chunk`
SET `index_status` = CASE WHEN `deleted` = 1 THEN 'DELETED' ELSE COALESCE(NULLIF(`index_status`, ''), 'PENDING') END
WHERE `index_status` IS NULL OR `index_status` = '' OR `deleted` = 1;

CALL add_index_if_not_exists('question_embedding', 'idx_question_embedding_status',
  'INDEX `idx_question_embedding_status` (`index_status`, `updated_at`)');
CALL add_index_if_not_exists('personal_knowledge_chunk', 'idx_personal_chunk_index_status',
  'INDEX `idx_personal_chunk_index_status` (`user_id`, `index_status`, `updated_at`)');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
