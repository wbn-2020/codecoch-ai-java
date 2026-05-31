-- V4_022: Align RAG and dedupe schema with Java fingerprint/evaluation contracts.
-- Adds only nullable compatible columns and indexes; no destructive cleanup is performed.

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

CALL add_column_if_not_exists('personal_knowledge_document', 'normalization_version',
  'VARCHAR(32) DEFAULT ''text-fingerprint-v1'' COMMENT ''Java text fingerprint normalization version''', 'content_hash');
CALL add_column_if_not_exists('personal_knowledge_chunk', 'normalization_version',
  'VARCHAR(32) DEFAULT ''text-fingerprint-v1'' COMMENT ''Java text fingerprint normalization version''', 'chunk_hash');
CALL add_column_if_not_exists('personal_knowledge_document_version', 'normalization_version',
  'VARCHAR(32) DEFAULT ''text-fingerprint-v1'' COMMENT ''Java text fingerprint normalization version''', 'content_hash');

CALL add_column_if_not_exists('personal_knowledge_eval_case', 'retrieval_document_id',
  'BIGINT DEFAULT NULL COMMENT ''Optional document scope used when running retrieval evaluation''', 'expected_document_type');
CALL add_column_if_not_exists('personal_knowledge_eval_case', 'retrieval_document_type',
  'VARCHAR(64) DEFAULT NULL COMMENT ''Optional document type scope used when running retrieval evaluation''', 'retrieval_document_id');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'retrieval_document_id',
  'BIGINT DEFAULT NULL COMMENT ''Optional document scope used when running retrieval evaluation''', 'expected_document_type');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'retrieval_document_type',
  'VARCHAR(64) DEFAULT NULL COMMENT ''Optional document type scope used when running retrieval evaluation''', 'retrieval_document_id');

CALL add_column_if_not_exists('personal_knowledge_eval_result', 'citation_valid',
  'TINYINT DEFAULT NULL COMMENT ''1 if generated answer cites retrieved references correctly''', 'reference_count');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'answer_grounded',
  'TINYINT DEFAULT NULL COMMENT ''1 if generated answer is grounded by valid retrieved citations''', 'citation_valid');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'answer_excerpt',
  'VARCHAR(1000) DEFAULT NULL COMMENT ''Generated answer excerpt captured during evaluation''', 'answer_grounded');
CALL add_column_if_not_exists('personal_knowledge_eval_result', 'citation_warning',
  'VARCHAR(1000) DEFAULT NULL COMMENT ''Citation or grounding warning captured during evaluation''', 'answer_excerpt');

CALL add_index_if_not_exists('personal_knowledge_document', 'idx_pk_doc_user_hash_version',
  'INDEX `idx_pk_doc_user_hash_version` (`user_id`, `content_hash`, `normalization_version`)');
CALL add_index_if_not_exists('personal_knowledge_chunk', 'idx_pk_chunk_user_hash_version',
  'INDEX `idx_pk_chunk_user_hash_version` (`user_id`, `chunk_hash`, `normalization_version`)');
CALL add_index_if_not_exists('personal_knowledge_eval_case', 'idx_pk_eval_case_retrieval_scope',
  'INDEX `idx_pk_eval_case_retrieval_scope` (`user_id`, `retrieval_document_id`, `retrieval_document_type`)');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
