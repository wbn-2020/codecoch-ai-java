-- V3_017: BE-09/BE-10 compatibility fields for recommendation practice state and resume apply patches.

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
    SELECT 1
    FROM information_schema.columns
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
    SELECT 1
    FROM information_schema.statistics
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

CALL add_column_if_not_exists(
  'question_recommendation_item',
  'match_status',
  'VARCHAR(32) NOT NULL DEFAULT ''UNMATCHED_DRAFT'' COMMENT ''MATCHED official question or UNMATCHED_DRAFT AI draft''',
  'sort_order'
);

UPDATE question_recommendation_item
SET match_status = CASE WHEN question_id IS NULL THEN 'UNMATCHED_DRAFT' ELSE 'MATCHED' END,
    practice_status = CASE
      WHEN question_id IS NULL THEN 'NOT_PRACTICABLE'
      WHEN practice_status IS NULL OR practice_status = ''NOT_PRACTICABLE'' THEN 'UNPRACTICED'
      ELSE practice_status
    END
WHERE deleted = 0
  AND (match_status IS NULL OR match_status = '' OR match_status = 'UNMATCHED_DRAFT');

CALL add_column_if_not_exists('practice_record', 'recommendation_item_id', 'BIGINT DEFAULT NULL COMMENT ''question_recommendation_item id''', 'source');
CALL add_column_if_not_exists('practice_record', 'batch_id', 'BIGINT DEFAULT NULL COMMENT ''question_recommendation_batch id''', 'recommendation_item_id');
CALL add_column_if_not_exists('practice_record', 'source_type', 'VARCHAR(64) DEFAULT NULL COMMENT ''recommendation source type''', 'batch_id');
CALL add_column_if_not_exists('practice_record', 'source_id', 'BIGINT DEFAULT NULL COMMENT ''recommendation source id''', 'source_type');
CALL add_column_if_not_exists('practice_record', 'skill_profile_id', 'BIGINT DEFAULT NULL COMMENT ''skill_profile id from recommendation context''', 'source_id');
CALL add_column_if_not_exists('practice_record', 'study_plan_id', 'BIGINT DEFAULT NULL COMMENT ''study_plan id from recommendation context''', 'skill_profile_id');

CALL add_index_if_not_exists('question_recommendation_item', 'idx_qri_match_status', 'KEY `idx_qri_match_status` (`match_status`, `deleted`)');
CALL add_index_if_not_exists('practice_record', 'idx_pr_recommendation_item', 'KEY `idx_pr_recommendation_item` (`recommendation_item_id`)');
CALL add_index_if_not_exists('practice_record', 'idx_pr_recommendation_batch', 'KEY `idx_pr_recommendation_batch` (`batch_id`)');
CALL add_index_if_not_exists('practice_record', 'idx_pr_source_context', 'KEY `idx_pr_source_context` (`source_type`, `source_id`)');
CALL add_index_if_not_exists('practice_record', 'idx_pr_skill_profile', 'KEY `idx_pr_skill_profile` (`skill_profile_id`)');
CALL add_index_if_not_exists('practice_record', 'idx_pr_study_plan', 'KEY `idx_pr_study_plan` (`study_plan_id`)');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
