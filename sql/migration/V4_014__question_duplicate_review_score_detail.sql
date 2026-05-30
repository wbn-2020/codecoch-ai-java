-- V4_014: Structured scoring metadata for question duplicate review.

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

CALL add_column_if_not_exists('question_duplicate_review', 'score_band',
  'VARCHAR(32) DEFAULT NULL COMMENT ''Semantic score band: STRONG or REVIEW''', 'match_reason');
CALL add_column_if_not_exists('question_duplicate_review', 'score_detail_json',
  'LONGTEXT DEFAULT NULL COMMENT ''Structured score parts JSON''', 'score_band');

UPDATE `question_duplicate_review`
SET `score_band` = CASE
    WHEN `match_reason` LIKE '%scoreBand=STRONG%' THEN 'STRONG'
    WHEN `match_reason` LIKE '%scoreBand=REVIEW%' THEN 'REVIEW'
    ELSE `score_band`
  END
WHERE `score_band` IS NULL
  AND `match_type` = 'SEMANTIC_SIMILAR'
  AND `deleted` = 0;

CALL add_index_if_not_exists('question_duplicate_review', 'idx_question_duplicate_score_band',
  'INDEX `idx_question_duplicate_score_band` (`score_band`, `similarity_score`)');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
