-- CodeCoachAI V2 B10: dashboard support and real study task calendar dates.
-- Scope: add planned_date to study_task and backfill deterministically from plan creation date and stage number.

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
  'study_task',
  'planned_date',
  'DATE DEFAULT NULL COMMENT ''Planned calendar date for the study task''',
  'stage_no'
);

UPDATE study_task t
JOIN study_plan p ON p.id = t.plan_id
SET t.planned_date = DATE_ADD(DATE(p.created_at), INTERVAL GREATEST(COALESCE(t.stage_no, 1), 1) - 1 DAY)
WHERE t.planned_date IS NULL;

CALL add_index_if_not_exists(
  'study_task',
  'idx_study_task_user_planned_date',
  'KEY `idx_study_task_user_planned_date` (`user_id`, `planned_date`, `task_status`)'
);

CALL add_index_if_not_exists(
  'study_task',
  'idx_study_task_plan_planned_date',
  'KEY `idx_study_task_plan_planned_date` (`plan_id`, `planned_date`, `task_order`)'
);

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
