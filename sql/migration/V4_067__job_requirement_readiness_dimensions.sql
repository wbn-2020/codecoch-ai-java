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

ALTER TABLE `job_requirement_evidence`
  MODIFY COLUMN `project_evidence_id` BIGINT DEFAULT NULL COMMENT 'optional project evidence id';

CALL add_column_if_not_exists('job_requirement_evidence', 'evidence_type',
  'VARCHAR(32) NOT NULL DEFAULT ''PROJECT_EVIDENCE'' COMMENT ''PROJECT_EVIDENCE/RESUME_MATCH/INTERVIEW_REPORT/APPLICATION_RESULT/QUESTION_PRACTICE''',
  'project_skill_evidence_id');
CALL add_column_if_not_exists('job_requirement_evidence', 'evidence_id',
  'BIGINT DEFAULT NULL COMMENT ''source aggregate id''', 'evidence_type');
CALL add_column_if_not_exists('job_requirement_evidence', 'evidence_sub_id',
  'BIGINT DEFAULT NULL COMMENT ''source detail/session id''', 'evidence_id');
CALL add_column_if_not_exists('job_requirement_evidence', 'title',
  'VARCHAR(255) DEFAULT NULL COMMENT ''evidence display title''', 'evidence_sub_id');
CALL add_column_if_not_exists('job_requirement_evidence', 'excerpt',
  'TEXT DEFAULT NULL COMMENT ''evidence excerpt''', 'title');
CALL add_column_if_not_exists('job_requirement_evidence', 'result_source',
  'VARCHAR(64) DEFAULT NULL COMMENT ''trusted business result/status source''', 'excerpt');
CALL add_column_if_not_exists('job_requirement_evidence', 'result_score',
  'INT DEFAULT NULL COMMENT ''normalized source score 0-100''', 'result_source');
CALL add_column_if_not_exists('job_requirement_evidence', 'occurred_at',
  'DATETIME DEFAULT NULL COMMENT ''business evidence occurrence time''', 'result_score');
CALL add_column_if_not_exists('job_readiness_snapshot', 'dimension_json',
  'LONGTEXT DEFAULT NULL COMMENT ''five-dimension readiness snapshot''', 'matrix_json');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
