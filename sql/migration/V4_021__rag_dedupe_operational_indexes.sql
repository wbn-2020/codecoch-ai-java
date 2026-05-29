-- V4_021: Operational indexes for RAG vector maintenance and question dedupe review flows.
-- This migration intentionally avoids data cleanup or new unique constraints so it can run safely
-- on existing databases that may already contain historical duplicate or reversed pairs.

DROP PROCEDURE IF EXISTS add_index_if_not_exists;

DELIMITER //

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

CALL add_index_if_not_exists('question_duplicate_review', 'idx_qdr_pair_status',
  'INDEX `idx_qdr_pair_status` (`source_question_id`, `target_question_id`, `review_status`)');
CALL add_index_if_not_exists('question_duplicate_review', 'idx_qdr_status_score_updated',
  'INDEX `idx_qdr_status_score_updated` (`review_status`, `score_band`, `updated_at`)');

CALL add_index_if_not_exists('question_relation', 'idx_qr_active_source_type',
  'INDEX `idx_qr_active_source_type` (`source_question_id`, `relation_status`, `relation_type`, `target_question_id`)');
CALL add_index_if_not_exists('question_relation', 'idx_qr_active_target_type',
  'INDEX `idx_qr_active_target_type` (`target_question_id`, `relation_status`, `relation_type`, `source_question_id`)');

CALL add_index_if_not_exists('question_embedding', 'idx_question_embedding_deleted_updated',
  'INDEX `idx_question_embedding_deleted_updated` (`deleted`, `updated_at`)');
CALL add_index_if_not_exists('question_embedding', 'idx_question_embedding_deleted_status_updated',
  'INDEX `idx_question_embedding_deleted_status_updated` (`deleted`, `index_status`, `updated_at`)');

CALL add_index_if_not_exists('personal_knowledge_chunk', 'idx_pk_chunk_status_updated',
  'INDEX `idx_pk_chunk_status_updated` (`index_status`, `updated_at`)');
CALL add_index_if_not_exists('personal_knowledge_chunk', 'idx_pk_chunk_deleted_status_updated',
  'INDEX `idx_pk_chunk_deleted_status_updated` (`deleted`, `index_status`, `updated_at`, `document_id`)');

CALL add_index_if_not_exists('vector_delete_outbox', 'idx_vector_delete_scope_status',
  'INDEX `idx_vector_delete_scope_status` (`collection_name`, `biz_type`, `status`, `updated_at`)');

DROP PROCEDURE IF EXISTS add_index_if_not_exists;
