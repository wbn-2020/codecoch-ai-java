-- CodeCoachAI V2 B7: idempotent schema guard for prior V2 migrations.
-- Scope: repair rerun risks from V2_003, V2_010, and V2_011 without changing business behavior.

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

CALL add_index_if_not_exists('industry_template', 'uk_industry_code', 'UNIQUE KEY `uk_industry_code` (`industry_code`)');
CALL add_index_if_not_exists('industry_template', 'idx_enabled_sort', 'KEY `idx_enabled_sort` (`enabled`, `sort_order`)');
CALL add_index_if_not_exists('industry_template', 'idx_deleted', 'KEY `idx_deleted` (`deleted`)');
CALL add_column_if_not_exists('interview_session', 'industry_template_id', 'BIGINT DEFAULT NULL COMMENT ''行业模板ID''', 'experience_level');
CALL add_column_if_not_exists('interview_session', 'industry_context', 'LONGTEXT DEFAULT NULL COMMENT ''行业上下文快照''', 'industry_direction');

CALL add_column_if_not_exists('resume', 'source_resume_id', 'BIGINT DEFAULT NULL', 'status');
CALL add_column_if_not_exists('resume', 'source_optimize_record_id', 'BIGINT DEFAULT NULL', 'source_resume_id');
CALL add_column_if_not_exists('resume', 'applied_at', 'DATETIME DEFAULT NULL', 'source_optimize_record_id');
CALL add_index_if_not_exists('resume', 'idx_resume_source_resume', 'KEY `idx_resume_source_resume` (`source_resume_id`)');
CALL add_index_if_not_exists('resume', 'idx_resume_source_optimize_record', 'KEY `idx_resume_source_optimize_record` (`source_optimize_record_id`)');

CALL add_column_if_not_exists('practice_record', 'answer_duration_seconds', 'INT DEFAULT NULL', 'answer_content');
CALL add_column_if_not_exists('practice_record', 'source', 'VARCHAR(64) NOT NULL DEFAULT ''QUESTION_BANK''', 'answer_duration_seconds');
CALL add_column_if_not_exists('practice_record', 'level', 'VARCHAR(32) DEFAULT NULL', 'score');
CALL add_column_if_not_exists('practice_record', 'strengths', 'TEXT', 'knowledge_points');
CALL add_column_if_not_exists('practice_record', 'weaknesses', 'TEXT', 'strengths');
CALL add_column_if_not_exists('practice_record', 'improvement_suggestions', 'TEXT', 'weaknesses');
CALL add_column_if_not_exists('practice_record', 'reference_comparison', 'TEXT', 'improvement_suggestions');
CALL add_column_if_not_exists('practice_record', 'knowledge_gaps', 'TEXT', 'reference_comparison');
CALL add_column_if_not_exists('practice_record', 'suggested_follow_ups', 'TEXT', 'knowledge_gaps');
CALL add_column_if_not_exists('practice_record', 'reference_answer_snapshot', 'TEXT', 'suggested_follow_ups');
CALL add_column_if_not_exists('practice_record', 'question_snapshot_json', 'LONGTEXT', 'reference_answer_snapshot');
CALL add_column_if_not_exists('practice_record', 'review_json', 'LONGTEXT', 'question_snapshot_json');

UPDATE practice_record
SET reference_answer_snapshot = reference_answer
WHERE reference_answer_snapshot IS NULL
  AND reference_answer IS NOT NULL;

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
