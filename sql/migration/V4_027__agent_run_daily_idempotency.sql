-- Add an explicit idempotency guard for daily JobCoachAgent runs.
-- This migration is intentionally not executed by Codex; review duplicate active rows before running in a real database.

SET @db_name = DATABASE();

DELIMITER //
DROP PROCEDURE IF EXISTS assert_agent_run_daily_idempotency_ready//
CREATE PROCEDURE assert_agent_run_daily_idempotency_ready()
BEGIN
  DECLARE duplicate_count BIGINT DEFAULT 0;

  SELECT COUNT(1)
    INTO duplicate_count
  FROM (
    SELECT user_id, agent_type, IFNULL(target_job_id, 0) AS target_job_key, plan_date
    FROM agent_run
    WHERE deleted = 0
      AND status IN ('RUNNING', 'SUCCESS')
    GROUP BY user_id, agent_type, IFNULL(target_job_id, 0), plan_date
    HAVING COUNT(1) > 1
  ) duplicated_active_runs;

  IF duplicate_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V4_027 found duplicate active agent_run rows for the same user/agent/target/date. Review and cancel stale RUNNING/SUCCESS rows before adding the unique idempotency guard.';
  END IF;
END//
DELIMITER ;

CALL assert_agent_run_daily_idempotency_ready();
DROP PROCEDURE IF EXISTS assert_agent_run_daily_idempotency_ready;

SET @column_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'agent_run'
    AND column_name = 'active_target_job_key'
);

SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE `agent_run`
     ADD COLUMN `active_target_job_key` BIGINT
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 AND `status` IN (''RUNNING'', ''SUCCESS'') THEN IFNULL(`target_job_id`, 0) ELSE NULL END) STORED
       COMMENT ''Active daily-run idempotency key for nullable target_job_id''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'agent_run'
    AND column_name = 'active_plan_date'
);

SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE `agent_run`
     ADD COLUMN `active_plan_date` DATE
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 AND `status` IN (''RUNNING'', ''SUCCESS'') THEN `plan_date` ELSE NULL END) STORED
       COMMENT ''Active daily-run idempotency key for plan_date''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'agent_run'
    AND index_name = 'uk_agent_run_user_agent_target_date_live'
);

SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE `agent_run`
     ADD UNIQUE KEY `uk_agent_run_user_agent_target_date_live`
       (`user_id`, `agent_type`, `active_target_job_key`, `active_plan_date`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
